package com.compassplus;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayOutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: arudin
 * Date: 12/7/11
 * Time: 11:41 AM
 * To change this template use File | Settings | File Templates.
 */
public class XMLUtils {
    private static XMLUtils ourInstance = new XMLUtils();
    private XPath xPath = XPathFactory.newInstance().newXPath();
    private static javax.xml.transform.TransformerFactory tFactory = null;
    private static final String defaultEnc = "UTF8";

    public static XMLUtils getInstance() {
        return ourInstance;
    }

    public static String applyXSL
            (String
                     XMLText, String
                    XSLText) throws TransformerException, UnsupportedEncodingException {

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

    public Node getNode(String xPath, Object src) {
        try {
            return (Node) this.xPath.evaluate(xPath, src, XPathConstants.NODE);
        } catch (Exception e) {
            return null;
        }
    }

    public String getString(Node node) {
        if (node != null) {
            if (node.getTextContent() != null) {
                if (!node.getTextContent().equals("")) {
                    return node.getTextContent();
                }
            }
        }
        return null;
    }

    private Document getDocumentFromSource(Reader source) {
        try {
            Document doc;
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            db.setEntityResolver(null);
            doc = db.parse(new InputSource(source));
            doc.normalize();
            return doc;
        } catch (Exception e) {
        }
        return null;
    }

    public Document getDocumentFromString(String string) {
        return getDocumentFromSource(new StringReader(string));
    }
}
