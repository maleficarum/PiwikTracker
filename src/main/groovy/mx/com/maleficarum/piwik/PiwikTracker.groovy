package mx.com.maleficarum.piwik

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import org.slf4j.LoggerFactory
import org.json.JSONArray

/**
 * Post data to piwik server
 *
 * @author Oscar Ivan Hernandez [ oscar at angellore dot mx ]
 * Date: 03/02/12
 */
class PiwikTracker {

    def VERSION = 1
    def request
    def site
    def referer
    def pageUrl
    def ip
    def language
    def userAgent
    def url
    def visitorId
    def hasCookies = false
    def requestCookie
    def cookieSupport = false
    def visitorCustomVar = []
    def forcedDatetime
    def tokenAuth

    def logger = LoggerFactory.getLogger(getClass())

    public PiwikTracker(idSite, apiUrl, req) throws Exception {
        if(!apiUrl) {
            throw new IllegalArgumentException("You must provide an API url.")
        }

        site = idSite
        url = apiUrl
        visitorId = getHash(UUID.randomUUID().toString()).substring(0, 16);
        
        logger.info("Using ${url} to post track data.")

        if(req) {
            request = req
            referer = request.getHeader("Referer")
            pageUrl = request.getRequestURL().toString();
            ip = request.getRemoteAddr();
            language = request.getLocale().getLanguage();
            userAgent = request.getHeader("user-agent");

            if(request.getCookies()) {
                def cookies = request.getCookies();
                for (int i = 0; i < cookies.length; i++) {
                    def c = cookies[i];
                    if(c.getName().equals("piwik_visitor")) {

                        if(logger.isDebugEnabled()) {
                            logger.debug("Found tracking cookie ${c}");
                        }

                        hasCookies = true;
                        requestCookie = c;
                    }
                }
            }
        }
    }

    /**
     * This method must be called before send data
     * @param req
     */
    def setRequest(req) {
        request = req
        referer = request.getHeader("Referer")
        pageUrl = request.getRequestURL().toString();
        ip = request.getRemoteAddr();
        language = request.getLocale().getLanguage();
        userAgent = request.getHeader("user-agent");

        if(request.getCookies()) {
            def cookies = request.getCookies();
            for (int i = 0; i < cookies.length; i++) {
                def c = cookies[i];
                if(c.getName().equals("piwik_visitor")) {

                    if(logger.isDebugEnabled()) {
                        logger.debug("Found tracking cookie ${c}");
                    }

                    hasCookies = true;
                    requestCookie = c;
                }
            }
        }
    }

    /**
     * Tracks a page view
     *
     * @param string documentTitle Page view name as it will appear in Piwik reports
     * @return
     * @return string Response
     * @throws Exception
     */
    def doTrackPageView(documentTitle) throws Exception {
        sendRequest(getUrlTrackPageView(documentTitle))
    }

    /**
     * @param string $documentTitle Page view name as it will appear in Piwik reports
     * @return string URL to piwik.php with all parameters set to track the pageview
     * @throws Exception
     */
    def getUrlTrackPageView(documentTitle) throws Exception {
        def url = getStringRequest( site );
        if(!documentTitle) {
            url += "&action_name=" + urlencode(documentTitle);
        }
        url
    }

    protected String getStringRequest( int idSite ) throws Exception {
        def plugins
        def localHour
        def localMinute
        def localSecond
        def width
        def height
        def customData

        if(logger.isDebugEnabled()) {
            logger.debug("Posting info throug ${url}")
        }

        if(!url) {
            throw new Exception("You must first set the Piwik Tracker URL");
        }

        if( url.indexOf("/piwik.php") == -1	&& url.indexOf("/proxy-piwik.php") == -1) {
            url += "/piwik.php";
        }

        url +
                "?idsite=" + idSite +
                "&rec=1" +
                "&apiv=" + VERSION +
                "&url=" + urlencode(pageUrl) +
                "&urlref=" + urlencode(referer) +
                "&rand=" + getRandom() +
                "&_id=" + visitorId +
                "&_ref=" + urlencode(referer) +
                "&_refts=" + ( !forcedDatetime ? forcedDatetime : new Date()) +
                (!ip ? "&cip=" + ip : "") +
                (!forcedDatetime ? "&cdt=" + urlencode(forcedDatetime) : "") +
                (!tokenAuth ? "&token_auth=" + urlencode(tokenAuth) : "") +
                (!plugins ? plugins : "" ) +
                ((localHour && localMinute && localSecond) ? "&h=" + localHour + "&m=" + localMinute  + "&s=" + localSecond : "" )+
                ((!width && !height) ? "&res=" + width + "x" + height : "") +
                (hasCookies ? "&cookie=" + hasCookies : "") +
                (!customData ? "&data=" + customData : "") +
                (!visitorCustomVar ? "&_cvar=" + urlencode(jsonEncode(visitorCustomVar)) : "")
    }


    def jsonEncode(visitorCustomVar) {
        def json = new JSONArray()

        visitorCustomVar.each { it
            json.put(it)
        }
        json.toString()
    }

    def getRandom() {
        new Double(new Random().nextDouble()).toString();
    }

    def urlencode(string) {
        if(string) {
            try {
                URLEncoder.encode(string, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                string
            }
        } else {
            ""
        }
    }

    def sendRequest(urlString) {
        def responseData
        if(!cookieSupport) {
            requestCookie = null;
        }

        try {
            def url = new URL(urlString)
            def connection =  url.openConnection()

            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(600);
            connection.setRequestProperty("user-agent", userAgent);
            connection.setRequestProperty("Accept-Language", language);

            if(requestCookie) {
                connection.setRequestProperty("Cookie", requestCookie.getName() + "=" + requestCookie.getValue());
            }

            responseData = new ResponseData(connection);
            def cookies = responseData.getCookies();

            if(cookies.size() > 0) {
                if(cookies.get(cookies.size() - 1).getName().lastIndexOf("XDEBUG") == -1 && cookies.get(cookies.size() - 1).getValue().lastIndexOf("XDEBUG") == -1) {
                    requestCookie = cookies.get(cookies.size() - 1);
                }
            }

            if(logger.isDebugEnabled()) {
                logger.debug("Posted ${urlString} with response code ${connection.getResponseCode()}")
            }

            if(connection.getResponseCode() != 200) {
                logger.error(connection.getResponseCode() + " " + connection.getResponseMessage());
            }

            connection.disconnect();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
        responseData
    }

    /**
     * Sets Visitor Custom Variable
     *
     * @param int Custom variable slot ID from 1-5
     * @param string Custom variable name
     * @param string Custom variable value
     */
    public void setCustomVariable(id, name, value) {
        def list = [name, value]
        visitorCustomVar.set(id, list);
    }

    /**
     *  Generates an MD5 hash for given string
    */
    def getHash(input) {
        String hash = "";

        try {
            def b = MessageDigest.getInstance("MD5").digest(input.getBytes());
            def bi = new java.math.BigInteger(1, b);
            hash = bi.toString(16);
            while (hash.length() < 32) {
                hash = "0" + hash;
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        hash
    }
}
