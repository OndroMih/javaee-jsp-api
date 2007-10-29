

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * Portions Copyright Apache Software Foundation.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.apache.jasper.xmlparser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.XMLConstants;

import com.sun.org.apache.commons.logging.Log;
import com.sun.org.apache.commons.logging.LogFactory;
import org.apache.jasper.Constants;
import org.apache.jasper.JasperException;
import org.apache.jasper.compiler.Localizer;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.ls.LSResourceResolver;
import org.w3c.dom.ls.LSInput;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XML parsing utilities for processing web application deployment
 * descriptor and tag library descriptor files.  FIXME - make these
 * use a separate class loader for the parser to be used.
 *
 * @author Craig R. McClanahan
 * @version $Revision: 1.10 $ $Date: 2006/10/13 18:36:12 $
 */

public class ParserUtils {

    // Logger
    static Log log = LogFactory.getLog(ParserUtils.class);

    /**
     * An error handler for use when parsing XML documents.
     */
    static ErrorHandler errorHandler = new MyErrorHandler();

    /**
     * An entity resolver for use when parsing XML documents.
     */
    static EntityResolver entityResolver = new MyEntityResolver();

    /* SJSAS 6384538
    public static boolean validating = false;
    */

    static String schemaResourcePrefix;

    private static final String SCHEMA_LOCATION_ATTR = "schemaLocation";

    private static HashMap<String, Schema> schemaCache =
        new HashMap<String, Schema>();

    /**
     * List of the Public IDs that we cache, and their
     * associated location. This is used by 
     * an EntityResolver to return the location of the
     * cached copy of a DTD.
     */
    static final String[] CACHED_DTD_PUBLIC_IDS = {
	Constants.TAGLIB_DTD_PUBLIC_ID_11,
	Constants.TAGLIB_DTD_PUBLIC_ID_12,
	Constants.WEBAPP_DTD_PUBLIC_ID_22,
	Constants.WEBAPP_DTD_PUBLIC_ID_23,
    };

    // START PWC 6386258
    static final String[] CACHED_DTD_RESOURCE_PATHS = {
        Constants.TAGLIB_DTD_RESOURCE_PATH_11,
        Constants.TAGLIB_DTD_RESOURCE_PATH_12,
        Constants.WEBAPP_DTD_RESOURCE_PATH_22,
        Constants.WEBAPP_DTD_RESOURCE_PATH_23,
    };

    static final String[] CACHED_SCHEMA_RESOURCE_PATHS = {
        Constants.TAGLIB_SCHEMA_RESOURCE_PATH_20,
        Constants.TAGLIB_SCHEMA_RESOURCE_PATH_21,
        Constants.WEBAPP_SCHEMA_RESOURCE_PATH_24,
        Constants.WEBAPP_SCHEMA_RESOURCE_PATH_25,
    };
    // END PWC 6386258


    // --------------------------------------------------------- Static Methods


    // START PWC 6386258
    /**
     * Sets the path prefix for .xsd resources
     */
    public static void setSchemaResourcePrefix(String prefix) {

        schemaResourcePrefix = prefix;

        for (int i=0; i<CACHED_SCHEMA_RESOURCE_PATHS.length; i++) {
            String path = CACHED_SCHEMA_RESOURCE_PATHS[i];
            int index = path.lastIndexOf('/');
            if (index != -1) {
                CACHED_SCHEMA_RESOURCE_PATHS[i] =
                    prefix + path.substring(index+1);
            }
        }
    }

    /**
     * Sets the path prefix for .dtd resources
     */
    public static void setDtdResourcePrefix(String prefix) {
        for (int i=0; i<CACHED_DTD_RESOURCE_PATHS.length; i++) {
            String path = CACHED_DTD_RESOURCE_PATHS[i];
            int index = path.lastIndexOf('/');
            if (index != -1) {
                CACHED_DTD_RESOURCE_PATHS[i] =
                    prefix + path.substring(index+1);
            }
        }
    }
    // END PWC 6386258


    // --------------------------------------------------------- Public Methods

    /**
     * Parse the specified XML document, and return a <code>TreeNode</code>
     * that corresponds to the root node of the document tree.
     *
     * @param uri URI of the XML document being parsed
     * @param is Input source containing the deployment descriptor
     *
     * @exception JasperException if an I/O or parsing error has occurred
     */
    public TreeNode parseXMLDocument(String uri, InputSource is)
            throws JasperException {
        return parseXMLDocument(uri, is, false);
    }

    /**
     * Parse the specified XML document, and return a <code>TreeNode</code>
     * that corresponds to the root node of the document tree.
     *
     * @param uri URI of the XML document being parsed
     * @param is Input source containing the deployment descriptor
     * @param validate true if the XML document needs to be validated against
     * its DTD or schema, false otherwise
     *
     * @exception JasperException if an I/O or parsing error has occurred
     */
    public TreeNode parseXMLDocument(String uri, InputSource is,
                                     boolean validate)
            throws JasperException {

        Document document = null;

        // Perform an XML parse of this document, via JAXP
        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            /* See CR 6399139
            factory.setFeature(
                "http://apache.org/xml/features/validation/dynamic",
                true);
            */
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(entityResolver);
            builder.setErrorHandler(errorHandler);
            document = builder.parse(is);
            document.setDocumentURI(uri);
            if (validate) {
                Schema schema = getSchema(document);
                if (schema != null) {
                    // Validate TLD against specified schema
                    schema.newValidator().validate(new DOMSource(document));
                }
                /* See CR 6399139
                else {
                    log.warn(Localizer.getMessage(
                        "jsp.warning.dtdValidationNotSupported"));
                }
                */
            }
	} catch (ParserConfigurationException ex) {
            throw new JasperException
                (Localizer.getMessage("jsp.error.parse.xml", uri), ex);
	} catch (SAXParseException ex) {
            throw new JasperException
                (Localizer.getMessage("jsp.error.parse.xml.line",
				      uri,
				      Integer.toString(ex.getLineNumber()),
				      Integer.toString(ex.getColumnNumber())),
		 ex);
	} catch (SAXException sx) {
            throw new JasperException
                (Localizer.getMessage("jsp.error.parse.xml", uri), sx);
        } catch (IOException io) {
            throw new JasperException
                (Localizer.getMessage("jsp.error.parse.xml", uri), io);
	}

        // Convert the resulting document to a graph of TreeNodes
        return (convert(null, document.getDocumentElement()));
    }


    /**
     * Parse the specified XML document, and return a <code>TreeNode</code>
     * that corresponds to the root node of the document tree.
     *
     * @param uri URI of the XML document being parsed
     * @param is Input stream containing the deployment descriptor
     *
     * @exception JasperException if an I/O or parsing error has occurred
     */
    public TreeNode parseXMLDocument(String uri, InputStream is)
            throws JasperException {
        return parseXMLDocument(uri, new InputSource(is), false);
    }

    /**
     * Parse the specified XML document, and return a <code>TreeNode</code>
     * that corresponds to the root node of the document tree.
     *
     * @param uri URI of the XML document being parsed
     * @param is Input stream containing the deployment descriptor
     * @param validate true if the XML document needs to be validated against
     * its DTD or schema, false otherwise
     *
     * @exception JasperException if an I/O or parsing error has occurred
     */
    public TreeNode parseXMLDocument(String uri, InputStream is,
                                     boolean validate)
            throws JasperException {
        return  parseXMLDocument(uri, new InputSource(is), validate);
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Create and return a TreeNode that corresponds to the specified Node,
     * including processing all of the attributes and children nodes.
     *
     * @param parent The parent TreeNode (if any) for the new TreeNode
     * @param node The XML document Node to be converted
     */
    protected TreeNode convert(TreeNode parent, Node node) {

        // Construct a new TreeNode for this node
        TreeNode treeNode = new TreeNode(node.getNodeName(), parent);

        // Convert all attributes of this node
        NamedNodeMap attributes = node.getAttributes();
        if (attributes != null) {
            int n = attributes.getLength();
            for (int i = 0; i < n; i++) {
                Node attribute = attributes.item(i);
                treeNode.addAttribute(attribute.getNodeName(),
                                      attribute.getNodeValue());
            }
        }

        // Create and attach all children of this node
        NodeList children = node.getChildNodes();
        if (children != null) {
            int n = children.getLength();
            for (int i = 0; i < n; i++) {
                Node child = children.item(i);
                if (child instanceof Comment)
                    continue;
                if (child instanceof Text) {
                    String body = ((Text) child).getData();
                    if (body != null) {
                        body = body.trim();
                        if (body.length() > 0)
                            treeNode.setBody(body);
                    }
                } else {
                    TreeNode treeChild = convert(treeNode, child);
                }
            }
        }
        
        // Return the completed TreeNode graph
        return (treeNode);
    }


    // -------------------------------------------------------- Private Methods

    /*
     * Gets the compiled schema referenced by the given XML document.
     *
     * @param document The XML document to validate
     *
     * @return The schema against which to validate
     */
    private static Schema getSchema(Document document)
            throws SAXException, JasperException {

        Schema schema = null;
        Element root = document.getDocumentElement();
        NamedNodeMap map = root.getAttributes();
        for (int i=0; map!=null && i<map.getLength(); i++) {
            if (SCHEMA_LOCATION_ATTR.equals(map.item(i).getLocalName())) {
                String schemaLocation = map.item(i).getNodeValue();
                if (Constants.SCHEMA_LOCATION_JSP_20.equals(schemaLocation)) {
                    schema = getSchema(
                            Constants.TAGLIB_SCHEMA_PUBLIC_ID_20);
                    break;
                } else if (Constants.SCHEMA_LOCATION_JSP_21.equals(
                                                schemaLocation)) {
                    schema = getSchema(
                            Constants.TAGLIB_SCHEMA_PUBLIC_ID_21);
                    break;
                } else if (Constants.SCHEMA_LOCATION_WEBAPP_24.equals(
                                                schemaLocation)) {
                    schema = getSchema(
                            Constants.WEBAPP_SCHEMA_PUBLIC_ID_24);
                    break;
                } else if (Constants.SCHEMA_LOCATION_WEBAPP_25.equals(
                                                schemaLocation)) {
                    schema = getSchema(
                            Constants.WEBAPP_SCHEMA_PUBLIC_ID_25);
                    break;
                } else {
                    throw new JasperException(Localizer.getMessage(
                        "jsp.error.parse.unknownTldSchemaLocation",
                        document.getDocumentURI(),
                        map.item(i).getNodeValue()));
                }
            }
        }

        return schema;
    }


    /*
     * Gets the compiled schema for the given schema public id.
     *
     * @param schemaPublicId The public id for which to get the schema
     * (e.g., web-jsptaglibrary_2_0.xsd)
     *
     * @return The compiled schema
     */
    private static Schema getSchema(String schemaPublicId)
            throws SAXException {

        Schema schema = schemaCache.get(schemaPublicId);
        if (schema == null) {
            synchronized (schemaCache) {
                schema = schemaCache.get(schemaPublicId);
                if (schema == null) {
                    SchemaFactory schemaFactory = SchemaFactory.newInstance(
                        XMLConstants.W3C_XML_SCHEMA_NS_URI);
                    schemaFactory.setResourceResolver(
                        new MyLSResourceResolver());
                    schemaFactory.setErrorHandler(new MyErrorHandler());
                    schema = schemaFactory.newSchema(new StreamSource(
                    ParserUtils.class.getResourceAsStream(
                        schemaResourcePrefix + schemaPublicId)));
                    schemaCache.put(schemaPublicId, schema);
                }
            }
        }

        return schema;
    }

}


// ------------------------------------------------------------ Private Classes

class MyEntityResolver implements EntityResolver {
    
    public InputSource resolveEntity(String publicId, String systemId)
	throws SAXException
    {
	for (int i=0; i<ParserUtils.CACHED_DTD_PUBLIC_IDS.length; i++) {
	    String cachedDtdPublicId = ParserUtils.CACHED_DTD_PUBLIC_IDS[i];
	    if (cachedDtdPublicId.equals(publicId)) {
                /* PWC 6386258
		String resourcePath = Constants.CACHED_DTD_RESOURCE_PATHS[i];
                */
                // START PWC 6386258
                String resourcePath = ParserUtils.CACHED_DTD_RESOURCE_PATHS[i];
                // END PWC 6386258
		InputStream input =
		    this.getClass().getResourceAsStream(resourcePath);
		if (input == null) {
		    throw new SAXException(
                        Localizer.getMessage("jsp.error.internal.filenotfound",
					     resourcePath));
		}
		InputSource isrc = new InputSource(input);
		return isrc;
	    }
	}
        if (ParserUtils.log.isDebugEnabled())
            ParserUtils.log.debug("Resolve entity failed"  + publicId + " "
                                  + systemId );
	ParserUtils.log.error(
            Localizer.getMessage("jsp.error.parse.xml.invalidPublicId",
            publicId));
        return null;
    }
}

class MyErrorHandler implements ErrorHandler {
    public void warning(SAXParseException ex)
	throws SAXException
    {
        if (ParserUtils.log.isDebugEnabled())
            ParserUtils.log.debug("ParserUtils: warning ", ex );
	// We ignore warnings
    }

    public void error(SAXParseException ex)
	throws SAXException
    {
	throw ex;
    }

    public void fatalError(SAXParseException ex)
	throws SAXException
    {
	throw ex;
    }
}

class MyLSResourceResolver implements LSResourceResolver {

    public LSInput resolveResource(String type, 
                                   String namespaceURI, 
                                   String publicId, 
                                   String systemId, 
                                   String baseURI) {

        InputStream is = null;

        String resourceName = systemId;
        int index = systemId.lastIndexOf('/');
        if (index != -1) {
            resourceName = systemId.substring(index+1);
        }
        String resourcePath = ParserUtils.schemaResourcePrefix + resourceName;
        is = this.getClass().getResourceAsStream(resourcePath);

        MyLSInput ls = new MyLSInput();
        ls.setByteStream(is);
        ls.setSystemId(systemId); // See CR 6402288

        return ls;
    }
}

class MyLSInput implements LSInput {

    private Reader charStream;
    private InputStream byteStream;
    private String stringData;
    private String systemId;
    private String publicId;
    private String baseURI;
    private String encoding;
    private boolean certifiedText;

    public Reader getCharacterStream() {
        return charStream;
    }

    public void setCharacterStream(Reader charStream) {
        this.charStream = charStream;
    }

    public InputStream getByteStream() {
        return byteStream;
    }

    public void setByteStream(InputStream byteStream) {
        this.byteStream = byteStream;
    }

    public String getStringData() {
        return stringData;
    }

    public void setStringData(String stringData) {
        this.stringData = stringData;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getBaseURI() {
        return baseURI;
    }

    public void setBaseURI(String baseURI) {
        this.baseURI = baseURI;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean getCertifiedText() {
        return certifiedText;
    }

    public void setCertifiedText(boolean certifiedText) {
        this.certifiedText = certifiedText;
    }
}