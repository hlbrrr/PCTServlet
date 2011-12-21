package com.compassplus;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Node;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.List;

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
    private static final String jsonXslPath = "/WEB-INF/transformToJSON.xsl";
    private static final String homeXslPath = "/WEB-INF/home.xsl";
    private static final String prepareCfgXslPath = "/WEB-INF/prepareCfg.xsl";
    private static final String uploadsPath = "/uploads";
    private static final String defaultEnc = "UTF8";
    private static DocumentBuilderFactory dbFactory = null;
    private static XMLUtils xut = XMLUtils.getInstance();
    private static final short ADMIN_USER = 1;
    private static final short REGULAR_USER = 2;
    private static final short UNKNOWN_USER = 3;

    private static final String ADMIN_PAGE = "/WEB-INF/admin_index.html";
    private static final String REGULAR_PAGE = "/WEB-INF/user_index.html";

    public PCTServlet() {
        super();
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setCharacterEncoding(defaultEnc);
        String CN = null;
        try {
            java.security.cert.X509Certificate[] certs;
            certs = (java.security.cert.X509Certificate[]) httpServletRequest.getAttribute("javax.servlet.request.X509Certificate");
            if (certs != null) {
                java.security.cert.X509Certificate clientCert = certs[0];
                if (clientCert != null) {
                    java.security.Principal userDN = clientCert.getSubjectDN();
                    CN = userDN.getName();
                    int s = CN.indexOf("CN=");
                    int f = s + CN.substring(s).indexOf(",");
                    CN = CN.substring(s + 3, f);
                }
            }
        } catch (Exception e) {
        }

        short uType = getUserType(CN);

        if (httpServletRequest.getParameter("z") != null) {
            uType = REGULAR_USER;
        } else {
            uType = ADMIN_USER;
        }

        if (uType != UNKNOWN_USER) {
            String action = null;

            try {
                action = getParameter(httpServletRequest, "action");
            } catch (Exception e) {
            }

            if (action == null && uType == ADMIN_USER) {
                getPage(ADMIN_PAGE, httpServletResponse);
            } else if (action == null && uType == REGULAR_USER) {
                //getPage(REGULAR_PAGE, httpServletResponse);
                getUserHome(httpServletResponse);
            } else if ("getConfig".equals(action) && uType == ADMIN_USER) {
                getConfig(httpServletRequest, httpServletResponse);
            } else if ("getHome".equals(action) && uType == ADMIN_USER) {
                getAdminHome(false, httpServletResponse);
            } else if ("downloadConfig".equals(action)) {
                downloadConfig(CN, httpServletRequest, httpServletResponse);
            } else if ("saveConfig".equals(action) && uType == ADMIN_USER) {
                saveConfig(httpServletRequest, httpServletResponse);
            } else if ("uploadFile".equals(action) && uType == ADMIN_USER) {
                uploadFile(httpServletRequest, httpServletResponse);
            } else {
                httpServletResponse.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
            }
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private void getAdminHome(boolean wrap, HttpServletResponse response) {
        String config = readConfig();
        response.setContentType("text/html");
        response.setCharacterEncoding("utf-8"); // for IE only
        if (config != null) {
            try {
                config = config.replace("<root>", "<root><path>" + uploadsPath + "</path>");
                if (wrap) {
                    config = config.replace("<root>", "<root><wrap>true</wrap>");
                }
                String result = null;
                result = xut.applyXSL(config, FileUtils.readFileToString(new File(this.getServletContext().getRealPath(homeXslPath)), defaultEnc));
                if (result != null) {
                    response.getOutputStream().write(result.getBytes(defaultEnc));
                }
            } catch (Exception e) {
                System.out.println("Can't validate config!");
                response.setStatus(552);
                e.printStackTrace();
            }
        } else {
            response.setStatus(551);
        }
    }

    private void getUserHome(HttpServletResponse response) {
        getAdminHome(true, response);
    }

    private void uploadFile(HttpServletRequest request, HttpServletResponse response) {
        boolean modern = false;
        try {
            modern = getParameter(request, "qqfile") != null ? true : false;
        } catch (Exception e) {
        }
        PrintWriter writer = null;
        response.setContentType("text/html");
        try {
            writer = response.getWriter();
        } catch (IOException ex) {
        }
        if (modern) {
            InputStream is = null;
            FileOutputStream fos = null;
            String filename = request.getHeader("X-File-Name");
            try {
                is = request.getInputStream();
                fos = new FileOutputStream(new File(this.getServletContext().getRealPath(uploadsPath) + "/" + filename));
                IOUtils.copy(is, fos);
                response.setStatus(HttpServletResponse.SC_OK);
                writer.print("{\"success\":true}");
            } catch (Exception e) {
                response.setStatus(response.SC_INTERNAL_SERVER_ERROR);
                writer.print("{\"success\":false}");
            } finally {
                try {
                    if (fos != null) {
                        fos.close();
                    }
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException ignored) {
                }
            }

        } else {
            ServletFileUpload upload = new ServletFileUpload(xut.getFileFactory());

            try {
                List<FileItem> items = upload.parseRequest(request);
                for (FileItem item : items) {
                    if (!item.isFormField()) {
                        String filename = item.getName();
                        if (filename != null) {
                            filename = FilenameUtils.getName(filename);
                            File uploadedFile = new File(this.getServletContext().getRealPath(uploadsPath) + "/" + filename);
                            item.write(uploadedFile);
                        }
                    }
                }
                response.setStatus(HttpServletResponse.SC_OK);
                writer.print("{\"success\":true}");
            } catch (Exception e) {
                response.setStatus(response.SC_INTERNAL_SERVER_ERROR);
                writer.print("{\"success\":false}");
            }
        }
        writer.flush();
        writer.close();
    }

    private void getPage(String path, HttpServletResponse httpServletResponse) {
        String page = null;
        try {
            page = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(path)), defaultEnc);
            httpServletResponse.getOutputStream().write(page.getBytes(defaultEnc));
        } catch (Exception e) {
            System.out.println("Can't read page file!");
            httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            e.printStackTrace();
        }
    }

    private short getUserType(String CN) {
        if (CN != null && !CN.equals("")) {
            try {
                String config = readConfig();
                if (config != null) {
                    Node userDeprecatedNode = xut.getNode("/root/Users/User[CN='" + CN + "']/Deprecated", xut.getDocumentFromString(config));
                    if (userDeprecatedNode != null && "false".equals(xut.getString(userDeprecatedNode))) {
                        Node userTypeNode = xut.getNode("/root/Users/User[CN='" + CN + "']/Admin", xut.getDocumentFromString(config));
                        if (userTypeNode != null && "true".equals(xut.getString(userTypeNode))) {
                            return ADMIN_USER;
                        } else {
                            return REGULAR_USER;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return UNKNOWN_USER;
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
        String config = readConfig();
        if (config != null) {
            try {
                String format = getParameter(httpServletRequest, "format");
                if ("json".equals(format) || format == null) {
                    String result = null;
                    result = xut.applyXSL(config.replace("\\", "\\\\"), FileUtils.readFileToString(new File(this.getServletContext().getRealPath(jsonXslPath)), defaultEnc)).replace("\\\\", "/").replace("\r", "").replace("\n", "\\n");
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
        } else {
            httpServletResponse.setStatus(551);
        }
    }

    private String readConfig() {
        String config = null;
        synchronized (configPath) {
            try {
                System.out.println("Reading config file..");
                config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath)), defaultEnc);
            } catch (Exception e) {
                System.out.println("Can't read config file!");
                e.printStackTrace();
            }
        }
        return config;
    }

    private void downloadConfig(String cn, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String config = readConfig();
        if (config != null) {
            try {
                String pwd = getParameter(httpServletRequest, "pwd");
                config = config.replace("<root>", "<root><cn>" + cn + "</cn>");
                config = xut.applyXSL(config, FileUtils.readFileToString(new File(this.getServletContext().getRealPath(prepareCfgXslPath)), defaultEnc));

                DesEncrypter ds = new DesEncrypter(pwd, defaultEnc);

                config = ds.encrypt(config);
                ds = null;
                pwd = null;

                httpServletResponse.setCharacterEncoding(defaultEnc);
                httpServletResponse.setContentType("text/plain");
                httpServletResponse.setHeader("Content-Disposition",
                        "attachment; filename=\"config.exml\"");
                httpServletResponse.getOutputStream().write(config.getBytes(defaultEnc));
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
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
