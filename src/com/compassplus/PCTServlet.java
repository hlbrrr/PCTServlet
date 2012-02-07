package com.compassplus;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: arudin
 * Date: 10/26/11
 * Time: 5:40 PM
 */
public class PCTServlet extends HttpServlet {
    private static final String configPath = "/WEB-INF/config";
    private static final String logPath = "/log";
    private static final String jsonXslPath = "/WEB-INF/transformToJSON.xsl";
    private static final String homeXslPath = "/WEB-INF/home.xsl";
    private static final String imageFilesXslPath = "/WEB-INF/images.xsl";
    private static final String prepareCfgXslPath = "/WEB-INF/prepareCfg.xsl";
    private static final String uploadsPath = "/uploads";
    private static final String log4jCfg = "/WEB-INF/log4j.xml";

    private static final String defaultEnc = "UTF8";
    private static DocumentBuilderFactory dbFactory = null;
    private static XMLUtils xut = XMLUtils.getInstance();
    private static final short ADMIN_USER = 1;
    private static final short REGULAR_USER = 2;
    private static final short UNKNOWN_USER = 3;
    private static final String DATE_FORMAT = "HH:mm dd/MM/yyyy";
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
    private static Logger logger;

    private static final String ADMIN_PAGE = "/WEB-INF/admin_index.html";

    private static final int LOCK_TIMEOUT = 120; // seconds
    private static Date lockedTime = null;
    private static String lockedBy = null;
    private static String cfgList = null;

    private static HashMap<String, Short> users = new HashMap<String, Short>();

    public PCTServlet() {
        super();
    }

    private void log(String CN, short userType, HttpServletRequest request, String message) {
        if (logger == null) {
            synchronized (logPath) {
                if (logger == null) {
                    System.setProperty("rootPath", this.getServletContext().getRealPath(logPath));
                    DOMConfigurator.configure(this.getServletContext().getRealPath(log4jCfg));
                    logger = Logger.getLogger("main");
                }
            }
        }
        String ip;
        if (request.getHeader("x-real-ip") != null) {
            ip = request.getHeader("x-real-ip");
        } else {
            ip = request.getRemoteAddr();
        }
        StringBuilder sb = new StringBuilder();
        if (userType == ADMIN_USER) {
            sb.append("[ADM]");
        } else if (userType == REGULAR_USER) {
            sb.append("[USR]");
        } else {
            sb.append("[!!!]");
        }
        sb.append("[");
        sb.append(CN);
        sb.append("-");
        sb.append(ip);
        sb.append("] ");
        sb.append(message);
        logger.info(sb.toString());
    }

    @Override
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        httpServletResponse.setContentType("text/html; charset=utf-8");
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

        //////////////////////////////
        /* try {
            CN = getParameter(httpServletRequest, "cn");
        } catch (Exception e) {
        }
        if (CN == null) {
            CN = (String) httpServletRequest.getSession().getAttribute("cn");
        } else {
            httpServletRequest.getSession().setAttribute("cn", CN);
        }*/
        //////////////////////////////

        short uType = getUserType(CN);
        if (uType != UNKNOWN_USER) {
            String action = null;

            try {
                action = getParameter(httpServletRequest, "action");
            } catch (Exception e) {
            }
            if (action == null && uType == ADMIN_USER) {
                getPage(ADMIN_PAGE, httpServletResponse);
                log(CN, uType, httpServletRequest, "Configurator opened");
            } else if (action == null && uType == REGULAR_USER) {
                //getPage(REGULAR_PAGE, httpServletResponse);
                getUserHome(httpServletResponse);
                log(CN, uType, httpServletRequest, "User page opened");
            } else if ("getConfig".equals(action) && uType == ADMIN_USER) {
                getConfig(httpServletRequest, httpServletResponse);
            } else if ("getHome".equals(action) && uType == ADMIN_USER) {
                getAdminHome(false, httpServletResponse);
            } else if ("downloadConfig".equals(action) && uType == ADMIN_USER) {
                downloadConfig(CN, httpServletRequest, httpServletResponse, true);
                log(CN, uType, httpServletRequest, "Configuration downloaded");
            } else if ("downloadConfig".equals(action) && uType == REGULAR_USER && getReleaseTimestamp() != null) {
                downloadConfig(CN, httpServletRequest, httpServletResponse, false);
                log(CN, uType, httpServletRequest, "Configuration downloaded");
            } else if ("saveConfig".equals(action) && uType == ADMIN_USER && (lockedTime == null || CN.equals(lockedBy))) {
                saveConfig(CN, httpServletRequest, httpServletResponse);
                log(CN, uType, httpServletRequest, "Configuration saved");
            } else if ("uploadFile".equals(action) && uType == ADMIN_USER && (lockedTime == null || CN.equals(lockedBy))) {
                uploadFile(httpServletRequest, httpServletResponse);
                log(CN, uType, httpServletRequest, "File uploaded");
            } else if ("loadBackup".equals(action) && uType == ADMIN_USER && (lockedTime == null || CN.equals(lockedBy))) {
                loadBackup(httpServletRequest);
                log(CN, uType, httpServletRequest, "Restored from backup");
            } else if ("releaseConfig".equals(action) && uType == ADMIN_USER && (lockedTime == null || CN.equals(lockedBy))) {
                releaseConfig(httpServletRequest);
                log(CN, uType, httpServletRequest, "Released configruration");
            } else if ("checkStatus".equals(action) && uType == ADMIN_USER) {
                checkStatus(CN, httpServletResponse);
            } else if ("setStatus".equals(action) && uType == ADMIN_USER) {
                boolean locked = false;
                try {
                    locked = "true".equals(getParameter(httpServletRequest, "locked"));
                } catch (Exception e) {
                }
                setStatus(CN, httpServletRequest, locked);
                log(CN, uType, httpServletRequest, locked ? "Configurator locked" : "Configurator unlocked");
            } else if ("imageFiles".equals(action) && uType == ADMIN_USER) {
                imageFiles(httpServletResponse);
            } else {
                httpServletResponse.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
            }
        } else {
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            log(CN, uType, httpServletRequest, "Unauthorized access");
        }
    }

    private void loadBackup(HttpServletRequest request) {
        try {
            String fileName = getParameter(request, "file");
            fileName = fileName.replace("/", "").replace("\\", "").replace("..", "");
            String config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath + "/" + fileName)), defaultEnc);
            synchronized (configPath) {
                try {
                    config = config.replaceAll("<Timestamp>.*</Timestamp>", "");
                    config = config.replace("<root>", "<root><Timestamp>" + System.currentTimeMillis() + "</Timestamp>");
                    String fullPath = this.getServletContext().getRealPath(configPath + "/config.xml");
                    System.out.println("Copying old config..");
                    copyFile(new File(fullPath), new File(fullPath + "_bck_" + System.currentTimeMillis()));
                    System.out.println("Saving new config..");
                    FileUtils.writeByteArrayToFile(new File(fullPath), config.getBytes(defaultEnc));
                    users.clear();
                    cfgList = null;
                    removeUnusedFiles(config);
                } catch (Exception e) {
                    System.out.println("Can't save config!");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Can't read config!");
            e.printStackTrace();
        }
    }

    private void releaseConfig(HttpServletRequest request) {
        try {
            String timestamp = getParameter(request, "timestamp");
            String config = readConfig();
            synchronized (configPath) {
                try {
                    config = config.replaceAll("<ReleaseTimestamp>.*</ReleaseTimestamp>", "");
                    config = config.replace("<root>", "<root><ReleaseTimestamp>" + timestamp + "</ReleaseTimestamp>");

                    String fullPath = this.getServletContext().getRealPath(configPath + "/config.xml");
                    System.out.println("Releasing config..");
                    FileUtils.writeByteArrayToFile(new File(fullPath), config.getBytes(defaultEnc));
                    //users.clear();
                    cfgList = null;
                    //removeUnusedFiles(config);
                } catch (Exception e) {
                    System.out.println("Can't release config!");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Can't read config!");
            e.printStackTrace();
        }
    }

    private void imageFiles(HttpServletResponse response) {
        try {
            if (cfgList == null) {
                synchronized (configPath) {
                    String path = this.getServletContext().getRealPath(configPath);
                    File folder = new File(path);
                    File[] listOfFiles = folder.listFiles();
                    StringBuilder result = new StringBuilder();
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        try {
                            if (listOfFiles[i].isFile()) {
                                sb.setLength(0);
                                sb.append("<File>");
                                if ("config.xml".equals(listOfFiles[i].getName())) {
                                    sb.append("<Current/>");
                                }
                                sb.append("<Name>");
                                sb.append(listOfFiles[i].getName());
                                sb.append("</Name>");
                                sb.append("<Description>");
                                Document cfg = xut.getDocumentFromString(FileUtils.readFileToString(listOfFiles[i].getAbsoluteFile(), defaultEnc));
                                Node timestamp = xut.getNode("/root/Timestamp", cfg);

                                Node descriptionNode = xut.getNode("/root/Comment", cfg);
                                if (descriptionNode != null && xut.getString(descriptionNode) != null) {
                                    sb.append(xut.getString(descriptionNode));
                                }
                                sb.append("</Description>");
                                if (getReleaseTimestamp() != null && getReleaseTimestamp().equals(xut.getString(timestamp))) {
                                    sb.append("<Release/>");
                                }
                                sb.append("<SavedBy>");
                                Node savedByNode = xut.getNode("/root/SavedBy", cfg);
                                if (savedByNode != null && xut.getString(savedByNode) != null) {
                                    sb.append(xut.getString(savedByNode));
                                }
                                sb.append("</SavedBy>");
                                if (timestamp != null && xut.getString(timestamp) != null) {
                                    sb.append("<Timestamp>");
                                    sb.append(xut.getString(timestamp));
                                    sb.append("</Timestamp>");
                                }
                                sb.append("<Date>");
                                if (timestamp != null && xut.getString(timestamp) != null) {
                                    Integer timestampInt = 0;
                                    try {
                                        timestampInt = Integer.parseInt(xut.getString(timestamp));
                                    } catch (Exception e) {

                                    }
                                    sb.append(simpleDateFormat.format(new Date(timestampInt)));
                                } else {
                                    sb.append(simpleDateFormat.format(new Date(listOfFiles[i].lastModified())));
                                }
                                sb.append("</Date><Sort>");
                                if (timestamp != null && xut.getString(timestamp) != null) {
                                    sb.append(xut.getString(timestamp));
                                } else {
                                    sb.append("0");
                                }
                                //sb.append(listOfFiles[i].lastModified());
                                sb.append("</Sort></File>");
                                result.append(sb);
                            }
                        } catch (Exception e) {
                        }
                    }
                    cfgList = result.toString();
                }
            }
            StringBuilder logList = new StringBuilder("<root>");
            {
                String path = this.getServletContext().getRealPath(logPath);
                File folder = new File(path);
                File[] listOfFiles = folder.listFiles();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < listOfFiles.length; i++) {
                    try {
                        if (listOfFiles[i].isFile()) {
                            sb.setLength(0);
                            sb.append("<Log>");
                            if ("log.txt".equals(listOfFiles[i].getName())) {
                                sb.append("<Current/>");
                            }
                            sb.append("<Name>");
                            sb.append(logPath + "/" + listOfFiles[i].getName());
                            sb.append("</Name>");
                            sb.append("<Date>");
                            sb.append(simpleDateFormat.format(new Date(listOfFiles[i].lastModified())));
                            sb.append("</Date><Sort>");
                            sb.append(listOfFiles[i].lastModified());
                            sb.append("</Sort></Log>");
                            logList.append(sb);
                        }
                    } catch (Exception e) {
                    }
                }
            }
            logList.append(cfgList);
            logList.append("</root>");
            String html = xut.applyXSL(logList.toString(), FileUtils.readFileToString(new File(this.getServletContext().getRealPath(imageFilesXslPath)), defaultEnc));
            if (html != null) {
                //response.setContentType("text/html");
                //response.setCharacterEncoding("utf-8"); // for IE only
                PrintWriter writer = null;
                try {
                    writer = response.getWriter();
                } catch (IOException ex) {
                }
                writer.write(html);
                writer.flush();
                writer.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkStatus(String cn, HttpServletResponse response) {
        StringBuilder result = new StringBuilder("{\"locked\":false}");
        if (lockedTime != null) {
            synchronized (configPath) {
                if (lockedTime != null) {
                    Calendar cal = GregorianCalendar.getInstance();
                    cal.setTime(lockedTime);
                    cal.add(Calendar.SECOND, LOCK_TIMEOUT);

                    if (cal.before(GregorianCalendar.getInstance())) {
                        /* expired */
                        lockedTime = null;
                        lockedBy = null;
                    } else {
                        result.setLength(0);
                        result.append("{\"locked\":true");
                        if (!cn.equals(lockedBy)) {
                            result.append(", \"by\":\"");
                            result.append(lockedBy);
                            result.append("\"");
                        } else {
                            lockedTime = new Date();
                        }
                        result.append("}");
                    }
                }
            }
        }
        try {
            response.getOutputStream().write(result.toString().getBytes(defaultEnc));
        } catch (Exception e) {
        }
    }

    private void setStatus(String cn, HttpServletRequest request, boolean locked) {
        synchronized (configPath) {
            if (locked) {
                lockedTime = new Date();
                lockedBy = cn;
            } else {
                lockedTime = null;
                lockedBy = null;
            }
        }
    }

    private void getAdminHome(boolean wrap, HttpServletResponse response) {
        String config = readConfig();
        //response.setContentType("text/html");
        //response.setCharacterEncoding("utf-8"); // for IE only
        if (config != null) {
            try {
                config = config.replace("<root>", "<root><path>" + uploadsPath + "</path>");
                if (wrap) {
                    config = config.replace("<root>", "<root><wrap>true</wrap>");
                }
                String result = xut.applyXSL(config, FileUtils.readFileToString(new File(this.getServletContext().getRealPath(homeXslPath)), defaultEnc));
                if (result != null) {
                    PrintWriter writer = null;
                    try {
                        writer = response.getWriter();
                    } catch (IOException ex) {
                    }
                    writer.write(result);
                    writer.flush();
                    writer.close();
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
            modern = getParameter(request, "qqfile") != null;
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
                writer.write("{\"success\":true}");
            } catch (Exception e) {
                response.setStatus(response.SC_INTERNAL_SERVER_ERROR);
                writer.write("{\"success\":false}");
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
                writer.write("{\"success\":true}");
            } catch (Exception e) {
                response.setStatus(response.SC_INTERNAL_SERVER_ERROR);
                writer.write("{\"success\":false}");
            }
        }
        writer.flush();
        writer.close();
    }

    private void getPage(String path, HttpServletResponse httpServletResponse) {
        String page;
        try {
            page = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(path)), defaultEnc);

            PrintWriter writer = null;
            try {
                writer = httpServletResponse.getWriter();
            } catch (IOException ex) {
            }
            writer.write(page);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            System.out.println("Can't read page file!");
            httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            e.printStackTrace();
        }
    }

    private short getUserType(String CN) {
        if (CN != null && !CN.equals("")) {
            try {
                if (users.containsKey(CN)) {
                    return users.get(CN);
                }
                String config = readConfig();
                if (config != null) {
                    Node userDeprecatedNode = xut.getNode("/root/Users/User[CN='" + CN + "']/Deprecated", xut.getDocumentFromString(config));
                    if (userDeprecatedNode != null && "false".equals(xut.getString(userDeprecatedNode))) {
                        Node userTypeNode = xut.getNode("/root/Users/User[CN='" + CN + "']/Admin", xut.getDocumentFromString(config));
                        if (userTypeNode != null && "true".equals(xut.getString(userTypeNode))) {
                            users.put(CN, ADMIN_USER);
                        } else {
                            users.put(CN, REGULAR_USER);
                        }
                        return users.get(CN);
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
            enc = "ISO-8859-1";
        }
        String val = httpServletRequest.getParameter(parameterName);
        return val != null ? new String(val.getBytes(enc), defaultEnc) : null;
    }

    private void getConfig(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String config = readConfig();
        if (config != null) {
            try {
                String format = getParameter(httpServletRequest, "format");
                if ("json".equals(format) || format == null) {
                    String result = xut.applyXSL(config.replace("\\", "\\\\"), FileUtils.readFileToString(new File(this.getServletContext().getRealPath(jsonXslPath)), defaultEnc)).replace("\\\\", "/").replace("\r", "").replace("\n", "\\n");
                    if (result != null) {
                        PrintWriter writer = null;
                        try {
                            writer = httpServletResponse.getWriter();
                        } catch (IOException ex) {
                        }
                        writer.write(result);
                        writer.flush();
                        writer.close();
                    }
                } else if ("xml".equals(format)) {
                    DocumentBuilder db = getDbFactory().newDocumentBuilder();
                    db.setEntityResolver(null);
                    db.parse(new ByteArrayInputStream(config.getBytes(defaultEnc)));
                    PrintWriter writer = null;
                    try {
                        writer = httpServletResponse.getWriter();
                    } catch (IOException ex) {
                    }
                    writer.write(config);
                    writer.flush();
                    writer.close();
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
        return readConfig(null);
    }

    private String readConfig(String timestamp) {
        String config = null;
        synchronized (configPath) {
            try {
                System.out.println("Reading config file " + (timestamp != null ? "[ts=" + timestamp + "]" : "") + "..");
                if (timestamp != null) {
                    String path = this.getServletContext().getRealPath(configPath);
                    File folder = new File(path);
                    File[] listOfFiles = folder.listFiles();
                    String releaseTimestamp = getReleaseTimestamp();
                    for (int i = 0; i < listOfFiles.length; i++) {
                        if (listOfFiles[i].isFile()) {
                            Document cfg = xut.getDocumentFromString(FileUtils.readFileToString(listOfFiles[i].getAbsoluteFile(), defaultEnc));
                            if (releaseTimestamp != null && releaseTimestamp.equals(xut.getString(xut.getNode("/root/Timestamp", cfg)))) {
                                config = FileUtils.readFileToString(listOfFiles[i].getAbsoluteFile(), defaultEnc);
                            }
                        }
                    }
                } else {
                    config = FileUtils.readFileToString(new File(this.getServletContext().getRealPath(configPath + "/config.xml")), defaultEnc);
                }
            } catch (Exception e) {
                System.out.println("Can't read config file!");
                e.printStackTrace();
            }
        }
        return config;
    }

    private String getReleaseTimestamp() {
        String config = readConfig();
        if (config != null) {
            Node releaseTimestamp = xut.getNode("/root/ReleaseTimestamp", xut.getDocumentFromString(config));
            return xut.getString(releaseTimestamp);
        }
        return null;
    }

    private void downloadConfig(String cn, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, boolean anyCfgAllowed) {
        String config = null;
        if (anyCfgAllowed) {
            String timestamp = null;
            try {
                timestamp = getParameter(httpServletRequest, "timestamp");
            } catch (Exception e) {
            }
            config = readConfig(timestamp);
        } else {
            config = readConfig(getReleaseTimestamp());
        }
        if (config != null) {
            try {
                String pwd = getParameter(httpServletRequest, "pwd");
                config = config.replace("<root>", "<root><cn>" + cn + "</cn>");
                config = xut.applyXSL(config, FileUtils.readFileToString(new File(this.getServletContext().getRealPath(prepareCfgXslPath)), defaultEnc));

                DesEncrypter ds = new DesEncrypter(pwd, defaultEnc);

                config = ds.encrypt(config);
                ds = null;
                pwd = null;

                //httpServletResponse.setCharacterEncoding(defaultEnc);
                httpServletResponse.setContentType("application/pct");
                httpServletResponse.setHeader("Content-Disposition",
                        "attachment; filename=\"config.exml\"");
                PrintWriter writer = null;
                try {
                    writer = httpServletResponse.getWriter();
                } catch (IOException ex) {
                }
                writer.write(config);
                writer.flush();
                writer.close();
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    }

    private void saveConfig(String CN, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        try {
            String config = getParameter(httpServletRequest, "config");
            config = config.replace("<root>", "<root><SavedBy>" + CN + "</SavedBy>");
            config = config.replace("<root>", "<root><Timestamp>" + System.currentTimeMillis() + "</Timestamp>");
            System.out.println("Config : ");
            System.out.println(config);
            System.out.println("Getting factory..");
            DocumentBuilder db = getDbFactory().newDocumentBuilder();
            db.setEntityResolver(null);
            System.out.println("Validating config..");
            db.parse(new ByteArrayInputStream(config.getBytes(defaultEnc)));
            synchronized (configPath) {
                try {
                    String fullPath = this.getServletContext().getRealPath(configPath + "/config.xml");
                    System.out.println("Copying old config..");
                    copyFile(new File(fullPath), new File(fullPath + "_bck_" + System.currentTimeMillis()));
                    System.out.println("Saving new config..");
                    FileUtils.writeByteArrayToFile(new File(fullPath), config.getBytes(defaultEnc));
                    users.clear();
                    cfgList = null;
                    removeUnusedFiles(config);
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

    private void removeUnusedFiles(String config) {
        try {
            Document doc = xut.getDocumentFromString(config);

            String path = this.getServletContext().getRealPath(uploadsPath);
            File folder = new File(path);
            File[] listOfFiles = folder.listFiles();
            String fileName;
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    fileName = listOfFiles[i].getName();
                    try {
                        if (xut.getNode("/root/Files/File[Key='" + fileName + "']", doc) == null) {
                            listOfFiles[i].delete();
                        }
                    } catch (Exception e) {

                    }
                }
            }

        } catch (Exception e) {

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
