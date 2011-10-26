package com.compassplus;

import org.apache.commons.io.FileUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: arudin
 * Date: 10/26/11
 * Time: 5:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class PCTServlet extends HttpServlet {
    private static final String configPath = "/config.xml";
    private static final String jsonXslPath = "/transformToJSON.xsl";
    private static final String defaultEnc = "UTF8";

    public PCTServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding(defaultEnc);
        String action = null;
        try {
            action = getParameter(httpServletRequest, "action");
        } catch (Exception e) {

        }
        if ("getConfig".equals(action)) {
            getConfig(httpServletRequest, httpServletResponse);
        } else if ("saveConfig".equals(action)) {
            saveConfig(httpServletRequest, httpServletResponse);
        } else {
            httpServletResponse.setStatus(501);
        }
    }

    @Override
    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        doGet(httpServletRequest, httpServletResponse);
    }

    private String getParameter(HttpServletRequest httpServletRequest, String parameterName) throws UnsupportedEncodingException {
        String enc = httpServletRequest.getCharacterEncoding();
        if (enc == null) {
            enc = defaultEnc;
        }
        String val = httpServletRequest.getParameter(parameterName);
        return new String(val.getBytes(enc), defaultEnc);
    }

    private void getConfig(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String config = null;
        synchronized (configPath) {
            try {
                config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath)), defaultEnc);
            } catch (Exception e) {
            }
        }
        if (config != null) {
            String result = null;
            try {
                result = applyXSL(config.replace("\\", "\\\\"), FileUtils.readFileToString(new File(this.getServletContext().getRealPath(jsonXslPath)), defaultEnc)).replace("\\\\", "/").replace("\r", "").replace("\n", "\\n");
                if (result != null) {
                    httpServletResponse.getOutputStream().write(result.getBytes(defaultEnc));
                    return;
                }
            } catch (Exception e) {
            }
        }
        httpServletResponse.setStatus(204);
    }

    private void saveConfig(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    }


    private static javax.xml.transform.TransformerFactory tFactory = null;

    private static String applyXSL(String XMLText, String XSLText) throws TransformerException, UnsupportedEncodingException {

        String declarationRegex = "<\\?.*?\\?>";
        XMLText = XMLText.replaceAll(declarationRegex, "");

        if (tFactory == null) {
            tFactory = javax.xml.transform.TransformerFactory.newInstance();
        }

        javax.xml.transform.Transformer transformer = tFactory.newTransformer(new javax.xml.transform.stream.StreamSource(new StringReader(XSLText), defaultEnc));
        ByteArrayOutputStream writer = new ByteArrayOutputStream();
        transformer.transform(new javax.xml.transform.stream.StreamSource(new StringReader(XMLText), defaultEnc), new javax.xml.transform.stream.StreamResult(writer));

        String returnData = writer.toString(defaultEnc);

        return returnData;
    }
}
