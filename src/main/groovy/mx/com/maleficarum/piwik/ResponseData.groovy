package mx.com.maleficarum.piwik

import javax.servlet.http.Cookie

/**
 * Created by IntelliJ IDEA.
 * User: angellore
 * Date: 03/02/12
 * Time: 11:01
 * To change this template use File | Settings | File Templates.
 */
class ResponseData {

    def data = [:]

    public ResponseData(connection) {
        data = connection.getHeaderFields()
    }

    def getCookies() {
        def cookies = []
        
        data.each { k, v ->
            def stringData = v
            def value = ""

            stringData.each {
                value += it
            }

            if (k && v) {
                // No more headers
                return
            } else if (k) {
                // The header value contains the server's HTTP version
            } else if(k.equals("Set-Cookie")) {
                def httpCookies = HttpCookie.parse(value)

                httpCookies.each { h ->
                    def c = new Cookie(h.getName(), h.getValue())
                    c.setComment(h.getComment())

                    if(h.getDomain()) {
                        c.setDomain(h.getDomain())
                    }
                    c.setMaxAge(new Long(h.getMaxAge()).intValue())
                    c.setPath(h.getPath())
                    c.setSecure(h.getSecure())
                    c.setVersion(h.getVersion())
                    cookies.add(c)
                }
            }
        }
        cookies
    }    
}
