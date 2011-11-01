package com.compassplus;

import org.apache.commons.io.FileUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: arudin
 * Date: 10/26/11
 * Time: 5:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class PCTServlet extends HttpServlet {
    private static final String configPath = "/WEB-INF/config/config.xml";
    private static final String changelog = "/WEB-INF/changelog.txt";
    private static final String jsonXslPath = "/transformToJSON.xsl";
    private static final String defaultEnc = "UTF8";
    private static DocumentBuilderFactory dbFactory = null;

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
        } else if ("downloadConfig".equals(action)) {
            downloadConfig(httpServletRequest, httpServletResponse);
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
                System.out.println("Reading config file..");
                config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath)), defaultEnc);
            } catch (Exception e) {
                System.out.println("Can't read file!");
                httpServletResponse.setStatus(551);
                e.printStackTrace();
            }
        }
        if (config != null) {
            try {
                String format = getParameter(httpServletRequest, "format");
                if ("json".equals(format) || format == null) {
                    String result = null;
                    result = applyXSL(config.replace("\\", "\\\\"), FileUtils.readFileToString(new File(this.getServletContext().getRealPath(jsonXslPath)), defaultEnc)).replace("\\\\", "/").replace("\r", "").replace("\n", "\\n");
                    if (result != null) {
                        httpServletResponse.getOutputStream().write(result.getBytes(defaultEnc));
                    }
                } else if ("xml".equals(format)) {
                    DocumentBuilder db = getDbFactory().newDocumentBuilder();
                    db.setEntityResolver(null);
                    db.parse(new ByteArrayInputStream(config.getBytes(defaultEnc)));
                    httpServletResponse.getOutputStream().write(config.getBytes(defaultEnc));
                } else {
                    System.out.println("Unknown format [" + format + "]!");
                    httpServletResponse.setStatus(553);
                }
            } catch (Exception e) {
                System.out.println("Can't validate config!");
                httpServletResponse.setStatus(552);
                e.printStackTrace();
            }
        }
    }

    private void downloadConfig(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String config = null;
        synchronized (configPath) {
            try {
                config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath)), defaultEnc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (config != null) {
            try {
                DocumentBuilder db = getDbFactory().newDocumentBuilder();
                db.setEntityResolver(null);

                db.parse(new ByteArrayInputStream(config.getBytes(defaultEnc)));
                httpServletResponse.setCharacterEncoding(defaultEnc);
                httpServletResponse.setContentType("text/plain");
                httpServletResponse.setHeader("Content-Disposition",
                        "attachment; filename=\"config.xml\"");
                httpServletResponse.getOutputStream().write(config.getBytes(defaultEnc));
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        httpServletResponse.setStatus(501);
    }

    private void saveConfig(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            String config = getParameter(httpServletRequest, "config");
            System.out.println("Config : ");
            System.out.println(config);
            System.out.println("Getting factory..");
            DocumentBuilder db = getDbFactory().newDocumentBuilder();
            db.setEntityResolver(null);
            System.out.println("Validating config..");
            db.parse(new ByteArrayInputStream(config.getBytes(defaultEnc)));
            synchronized (configPath) {
                try {
                    String fullPath = this.getServletContext().getRealPath(configPath);
                    System.out.println("Copying old config..");
                    copyFile(new File(fullPath), new File(fullPath + "_bck_" + System.currentTimeMillis()));
                    System.out.println("Saving new config..");
                    FileUtils.writeByteArrayToFile(new File(fullPath), config.getBytes(defaultEnc));
                    return;
                } catch (Exception e) {
                    httpServletResponse.setStatus(551);
                    System.out.println("Can't save config!");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            httpServletResponse.setStatus(552);
            System.out.println("Can't validate config!");
            e.printStackTrace();
        }
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

    private static void copyFile(File sourceFile, File destFile) throws IOException {
        if (!destFile.exists()) {
            destFile.createNewFile();
        }

        FileChannel source = null;
        FileChannel destination = null;
        try {
            source = new FileInputStream(sourceFile).getChannel();
            destination = new FileOutputStream(destFile).getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (destination != null) {
                destination.close();
            }
        }
    }

    private static DocumentBuilderFactory getDbFactory() {
        if (dbFactory == null) {
            dbFactory = DocumentBuilderFactory.newInstance();
        }
        return dbFactory;
    }
}
