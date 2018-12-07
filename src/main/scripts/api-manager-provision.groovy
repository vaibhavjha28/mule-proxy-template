import groovy.json.JsonSlurper
import groovy.json.JsonOutput 
import groovyx.net.http.*
import groovyx.net.http.ContentType.*
import groovyx.net.http.Method.*
class CICDUtil
{
    static def int WARN=1;
    static def int INFO=2;
    static def int DEBUG=3;
    static def int TRACE=4;
   
    static def logLevel = DEBUG;  //root logger level
    static def log (java.lang.Integer level, java.lang.Object content)
    {
        if (level <= logLevel)
        {
            def logPrefix = new Date().format("YYYYMMdd-HH:mm:ss") 
            if (level == WARN)
            {
                logPrefix += " WARN"
            }
            if (level == INFO)
            {
                logPrefix += " INFO"
            }
            if (level == DEBUG)
            {
                logPrefix += " DEBUG"
            }
            if (level == TRACE)
            {
                logPrefix += " TRACE"
            }
            println logPrefix + " : " + content 
        }
    }
   
    def getAnypointToken(props)
    {
        log(DEBUG,  "START getAnypointToken")
        def username=props.username
        def password=props.password 
        log(TRACE, "username=" + username)
        log(TRACE, "password=" + password)
        def urlString = "https://anypoint.mulesoft.com/accounts/login"
        def message = 'username='+username+'&password='+password
        def headers=["Content-Type":"application/x-www-form-urlencoded", "Accept": "application/json"]
        def connection = doRESTHTTPCall(urlString, "POST", message, headers)
        if ( connection.responseCode =~ '2..') 
        {
        }
        else
        {
            throw new Exception("Failed to get the login token!")
        }
        def response = "${connection.content}"
        def token = new JsonSlurper().parseText(response).access_token
        log(INFO, "Bearer Token: ${token}")
        log(DEBUG,  "END getAnypointToken")
        return token
    }
    
    def init ()
    {
        
              
        def props = ['username':System.properties.'anypoint.user', 
                     'password': System.properties.'anypoint.password',
                     'orgId': System.properties.'orgId',
                     'version': System.properties.'version',
                     'envId': System.properties.'envId',
                     'assetId': System.properties.'assetId',
                     'assetVersion': System.properties.'assetVersion',
                     'path': System.getProperty("user.dir"),
                     'clientIdEnforcementPolicy': System.properties.'clientIdEnforcementPolicy',
                     'rateLimitPolicy': System.properties.'rateLimitPolicy',
                     'oAuthPolicy': System.properties.'oAuthPolicy',
                     'timePeriod':System.properties.'timePeriod',
                     'maxRequests':System.properties.'maxRequests',
                     'scopes':System.properties.'scopes', 
                     'tokenUrl':System.properties.'tokenUrl',
                     'apiImplUri':System.properties.'apiImplUri',
                     'apiProxyUri':System.properties.'apiProxyUri',
                     'isCloudHub':System.properties.'isCloudHub',
                                          'apiType':System.properties.'apiType',
                     'deploymentType':System.properties.'deploymentType', 
                     'apiInstanceLabel':System.properties.'apiInstanceLabel',
                     'isHA':System.properties.'isHA',
                     'clusterName':System.properties.'clusterName'
                     ]
        log(DEBUG,  "props->" + props)
        return props;
    }
    def provisionAPIManager(props)
    {
        def token = getAnypointToken(props);
        
        def profileDetails = getProfile(token,props);
        def result = getAPIInstanceByExchangeAssetDetail(props, token, profileDetails);
        
        if ( props.clientIdEnforcementPolicy == "true")
        {
          def name = "clientIdEnforcementPolicy"
          def policyDetails = applyPolicy (token, result.apiDiscoveryId , props , name ,profileDetails)
        }
        
        if ( props.rateLimitPolicy == "true")
        {
          def name = "rateLimitPolicy"
          def policyDetails = applyPolicy (token, result.apiDiscoveryId , props , name , profileDetails)
        }
            
        if ( props.oAuthPolicy == "true")
        {
          def name = "oAuthPolicy"
          def policyDetails = applyPolicy (token, result.apiDiscoveryId , props , name , profileDetails)
        }
        log(INFO, "apiInstance=" + result)
        return result
    }
    
    def getProfile ( token , props )
    {
        log(DEBUG,  "START getProfile")
        
        def orgId = null
        def envId = null
        
        def urlString = "https://anypoint.mulesoft.com/accounts/api/profile"
        
        def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]
        
        def connection = doRESTHTTPCall(urlString, "GET", null, headers)
        
        def response = null
        def profDet = null 
        def allEnvIns = null
        def envIns = null
        
        if (connection.responseCode == 200)
            {
            
                log(INFO, " getProfile is successfull! statusCode=" + connection.responseCode)
                response = "${connection.content}"
                profDet = new JsonSlurper().parseText(response)
                //log(INFO, " Profile Details : " + profDet )
                
                orgId = profDet.organization.id
                
                allEnvIns = profDet.organization.environments
                
                allEnvIns.each {    
                
                log(INFO,"For each it : " + it)
                
                if (it.name == props.envId )
                       
                        {
                            envIns = it
                            envId = envIns.id
                            log(INFO, "Matched" + envId)
                        }
                      }
            }
        else
            {
            
                throw new Exception("Failed to get the profile statusCode=${connection.responseCode} responseMessage=${response}")
            
            }
            
       def profileDetails  = ["orgId": orgId, "envId": envId]
       
       log ( INFO , "ProfileDetails" + profileDetails )
       
       log(DEBUG,  "START getProfile")
       
       return profileDetails
    }
    def getAPIInstanceByExchangeAssetDetail(props, token , profileDetails)
    {
        log(DEBUG,  "START getAPIInstanceByExchangeAssetDetail")
        def apiInstance
        def apiDiscoveryName
        def apiDiscoveryVersion
        def apiDiscoveryId
        
        def urlString = "https://anypoint.mulesoft.com/exchange/api/v1/assets/"+profileDetails.orgId+"/"+props.assetId
        def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]
        def connection = doRESTHTTPCall(urlString, "GET", null, headers)
        if (connection.responseCode == 404)
        {
            log(INFO, "API Instance for " + props.assetId + " is not found in API Manager")
        } 
        else if (connection.responseCode == 200)
        {
            log(INFO, "API Instances for " + props.assetId + " has been found in the platform ");
            def response = "${connection.content}"
            def allAPIInstances = new JsonSlurper().parseText(response).instances;
          
            allAPIInstances.each{ 
                
                log(INFO, it)
                
                if (it.environmentId == profileDetails.envId && it.productAPIVersion == props.version && it.version == props.assetVersion && ( props.isHA == "false" || it.name == props.apiInstanceLabel) )
                
                  {                   
                    apiInstance = it;
                    apiDiscoveryName = "groupId:"+profileDetails.orgId+":assetId:"+ props.assetId
                    apiDiscoveryVersion = apiInstance.name
                    apiDiscoveryId = apiInstance.id
                    
                    log ( INFO , "This API Instance matched with the ArtifactID , ArtifactVersion & APIVersion provided : " + apiInstance ) 
                  }
                
            }
            log(INFO, "apiInstance for env " + profileDetails.envId + " is " + apiInstance);
        }
        if (apiInstance == null)
        {
            apiInstance = createAPIInstance(token, props , profileDetails)
            
            apiDiscoveryName = apiInstance.autodiscoveryInstanceName
            apiDiscoveryVersion = apiInstance.productVersion
            apiDiscoveryId = apiInstance.id
         
        }
        def result = ["apiInstance": apiInstance, "apiDiscoveryName": apiDiscoveryName, "apiDiscoveryVersion":apiDiscoveryVersion, "apiDiscoveryId": apiDiscoveryId]
        log(DEBUG,  "END getAPIInstanceByExchangeAssetDetail")
        return result
    }
    
    def applyPolicy (token, apiId , props, name , profileDetails )
    {
        log(DEBUG,  "START applyPolicy :" + name);
       
        def clientIdPolicy = /{"policyTemplateId": "294","groupId": "68ef9520-24e9-4cf2-b2f5-620025690913", "assetId": "client-id-enforcement", "assetVersion": "1.1.2", "configurationData": { "credentialsOriginHasHttpBasicAuthenticationHeader":"customExpression","clientIdExpression": "#[attributes.headers['client_id']]","clientSecretExpression": "#[attributes.headers['client_secret']]"}, "pointcutData":null}/ 
        
        def rateLimitPolicy = /{"policyTemplateId": "295","groupId": "68ef9520-24e9-4cf2-b2f5-620025690913","assetId": "rate-limiting","assetVersion": "1.2.1","configuration": {"rateLimits": [{"timePeriodInMilliseconds": null,"maximumRequests": null}], "clusterizable": true, "exposeHeaders": true},"pointcutData":null }/
        
        def oauthPolicy = /{"policyTemplateId": "302","groupId": "68ef9520-24e9-4cf2-b2f5-620025690913", "assetId": "external-oauth2-access-token-enforcement","assetVersion": "1.1.1", "configuration": {"scopes": null,"tokenUrl": null,"exposeHeaders": true }, "pointcutData":null }/
       
       def request = null
        
        if ( name == "clientIdEnforcementPolicy" )
        {  
           request = new JsonSlurper().parseText(clientIdPolicy);     
        }
        
        if ( name == "rateLimitPolicy" )
        {          
           request = new JsonSlurper().parseText(rateLimitPolicy);
          request.configuration.rateLimits[0].timePeriodInMilliseconds = props.timePeriod
          request.configuration.rateLimits[0].maximumRequests = props.maxRequests
        }
        
        if ( name == "oAuthPolicy" )
        {  
           request = new JsonSlurper().parseText(oauthPolicy); 
          request.configuration.scopes = props.scopes
          request.configuration.tokenUrl = props.tokenUrl
        }
        
        def message = JsonOutput.toJson(request)
        
        log(INFO, "applyPolicy request message for Policy : "+name + ", Message ->" + message);
        def urlString = "https://anypoint.mulesoft.com/apimanager/api/v1/organizations/"+profileDetails.orgId+"/environments/"+profileDetails.envId + "/apis/"+apiId+"/policies"
                       
        def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]
        def connection = doRESTHTTPCall(urlString, "POST", message, headers)
          
        def response = null
        
        def policy = null 
        
        if ( connection.responseCode =~ '2..') 
        {
            log(INFO, "the Policy: "+name + " is created successfully! statusCode=" + connection.responseCode)
            response = "${connection.content}" 
            policy = new JsonSlurper().parseText(response)
            log(DEBUG, "Policy Details "+ policy )
        }
        
        else if ( connection.responseCode =~ '409') 
        {
            log(INFO, "The Policy: "+name + " already exists , cannot overide ! statusCode=" + connection.responseCode)
        }
        
        else
        {
            throw new Exception("Failed to create Policy: "+name + "  ! statusCode=${connection.responseCode} responseMessage=${response}")
        }
        
        log(DEBUG,  "END applyPolicy: "+name)
        
        return policy
    
    }
    def createAPIInstance(token, props , profileDetails)
    {
        log(DEBUG,  "START createAPIInstance")
        def apiTemplate = /{ "spec": {"groupId": null,"assetId": null,"version": null},"endpoint": {"uri": null,"proxyUri": null,"isCloudHub": null,"muleVersion4OrAbove": true,"type": null,"deploymentType": null},"instanceLabel": null}/
        
        def request = new JsonSlurper().parseText(apiTemplate);
        request.spec.groupId = profileDetails.orgId
        request.spec.assetId = props.assetId
        request.spec.version = props.assetVersion
        request.endpoint.uri = props.apiImplUri
        
        if( props.apiProxyUri != "null" )
        {
        request.endpoint.proxyUri = props.apiProxyUri
        }
        
        if ( props.isCloudHub == "true" )
        {
          request.endpoint.isCloudHub = true
        }
        else 
        {
          request.endpoint.isCloudHub = false
        }
        
       // request.endpoint.muleVersion4OrAbove = props.muleVersion4OrAbove
        request.endpoint.type = props.apiType 
        request.endpoint.deploymentType = props.deploymentType
        request.instanceLabel = props.apiInstanceLabel
        def message = JsonOutput.toJson(request)
        
        log(INFO, "createAPIInstance request message=" + message);
        def urlString = "https://anypoint.mulesoft.com/apimanager/api/v1/organizations/"+profileDetails.orgId+"/environments/"+profileDetails.envId + "/apis"
        
        def headers=["Content-Type":"application/json", "Authorization": "Bearer " + token, "Accept": "application/json"]
              
        def connection = doRESTHTTPCall(urlString, "POST", message, headers)
            
        def response = "${connection.content}"
          
        if ( connection.responseCode =~ '2..') 
        {
            log(INFO, "the API instance is created successfully! statusCode=" + connection.responseCode)
        }
        else
        {
            throw new Exception("Failed to create API Instance! statusCode=${connection.responseCode} responseMessage=${response}")
        }
    
        def apiInstance = new JsonSlurper().parseText(response)
        log(DEBUG,  "END createAPIInstance")
        return apiInstance;
    }
    static def doRESTHTTPCall(urlString, method, payload, headers)
    {
        log(DEBUG,  "START doRESTHTTPCall")
        log(INFO, "requestURl is " + urlString)
        def url = new URL(urlString)
        def connection = url.openConnection()
        
        headers.keySet().each {
            log(INFO, it + "->" + headers.get(it))
            connection.setRequestProperty(it, headers.get(it))
        }
       
        connection.doOutput = true
        if (method == "POST")
        {
            connection.setRequestMethod("POST")
            def writer = new OutputStreamWriter(connection.outputStream)
            writer.write(payload)
            writer.flush()
            writer.close()
        }
        else if (method == "GET")
        {
            connection.setRequestMethod("GET")
        }
        
        connection.connect();
        
        log(DEBUG,  "END doRESTHTTPCall")
        return connection
    }
    
    def persisteAPIDiscoveryDetail (props, result)
    {
       
        
        Properties props1 = new Properties()
        def config = props.path+"/src/main/resources/config-"+props.envId+".properties"
        log(DEBUG,  "Config.properties path" + config )
        File propsFile = new File(config)
        props1.load(propsFile.newDataInputStream())
        log(DEBUG,  "Existing api.id=" + props1.getProperty('api.id') )
        props1.setProperty('api.id',result.apiDiscoveryId.toString())
        log(DEBUG,  "After change api.id=" + props1.getProperty('api.id') )
        props1.store(propsFile.newWriter(), null)
    }
    static void main(String[] args) {
        CICDUtil util = new CICDUtil();
        def props = util.init();
      
          
        def result = util.provisionAPIManager(props);
         
      
        util.persisteAPIDiscoveryDetail(props, result)
          
         
       } 
}