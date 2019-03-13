/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.xmlui.aspect.artifactbrowser;

import org.apache.cocoon.ResourceNotFoundException;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.http.HttpEnvironment;
import org.apache.log4j.Logger;
import org.dspace.app.xmlui.cocoon.AbstractDSpaceTransformer;
import org.dspace.app.xmlui.utils.AuthenticationUtil;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.app.xmlui.utils.UIException;
import org.dspace.app.xmlui.wing.Message;
import org.dspace.app.xmlui.wing.WingException;
import org.dspace.app.xmlui.wing.element.Body;
import org.dspace.app.xmlui.wing.element.Division;
import org.dspace.app.xmlui.wing.element.PageMeta;
import org.dspace.app.xmlui.wing.element.Para;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Display an item restricted message.
 *
 * @author Scott Phillips
 * @author Mark Diggory  mdiggory at atmire dot com
 * @author Fabio Bolognisi fabio at atmire dot com
 */
public class RestrictedItem extends AbstractDSpaceTransformer //implements CacheableProcessingComponent
{
    private static final Logger log = Logger.getLogger(RestrictedItem.class);

    /**
     * language strings
     */
    private static final Message T_title =
            message("xmlui.ArtifactBrowser.RestrictedItem.title");

    private static final Message T_dspace_home =
            message("xmlui.general.dspace_home");

    private static final Message T_trail =
            message("xmlui.ArtifactBrowser.RestrictedItem.trail");

    private static final Message T_head_resource =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_resource");

    private static final Message T_head_community =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_community");

    private static final Message T_head_collection =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_collection");

    private static final Message T_head_item =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_item");
    
    private static final Message T_head_item_withdrawn =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_item_withdrawn");

    private static final Message T_head_bitstream =
            message("xmlui.ArtifactBrowser.RestrictedItem.head_bitstream");

    private static final Message T_para_resource =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_resource");

    private static final Message T_para_community =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_community");

    private static final Message T_para_collection =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_collection");

    private static final Message T_para_item =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_item");
    
    private static final Message T_para_bitstream =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_bitstream");


    // Item states
    private static final Message T_para_item_restricted_auth =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_item_restricted_auth");
    private static final Message T_para_item_restricted =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_item_restricted");
    private static final Message T_para_item_withdrawn =
            message("xmlui.ArtifactBrowser.RestrictedItem.para_item_withdrawn");

    // Additional withdrawn message for items with an "is replaced by" URI
    private static final Message T_replaced_by = message("xmlui.ArtifactBrowser.ItemViewer.replaced_by");


    private static final Message T_para_login =
            message("xmlui.ArtifactBrowser.RestrictedItem.login");

    public void addPageMeta(PageMeta pageMeta) throws SAXException,
            WingException, UIException, SQLException, IOException,
            AuthorizeException 
    {
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);

        pageMeta.addMetadata("title").addContent(T_title);

        pageMeta.addTrailLink(contextPath + "/", T_dspace_home);
        if (dso != null) {
            HandleUtil.buildHandleTrail(dso, pageMeta, contextPath);
        }
        pageMeta.addTrail().addContent(T_trail);

    }


    public void addBody(Body body) throws SAXException, WingException,
            UIException, SQLException, IOException, AuthorizeException, 
            ResourceNotFoundException 
    {
        Request request = ObjectModelHelper.getRequest(objectModel);
        DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
        String handlePrefix = ConfigurationManager.getProperty("handle.prefix");
        String canonicalPrefix = ConfigurationManager.getProperty("handle.canonical.prefix");

        Division unauthorized = null;
        boolean isWithdrawn = false;
        
        if (dso == null) 
        {
            unauthorized = body.addDivision("unauthorized-resource", "primary");
            unauthorized.setHead(T_head_resource);
            unauthorized.addPara(T_para_resource);
        } 
        else if (dso instanceof Community) 
        {
            Community community = (Community) dso;
            unauthorized = body.addDivision("unauthorized-resource", "primary");
            unauthorized.setHead(T_head_community);
            unauthorized.addPara(T_para_community.parameterize(community.getMetadata("name")));
        } 
        else if (dso instanceof Collection) 
        {
            Collection collection = (Collection) dso;
            unauthorized = body.addDivision("unauthorized-resource", "primary");
            unauthorized.setHead(T_head_collection);
            unauthorized.addPara(T_para_collection.parameterize(collection.getMetadata("name")));
        } 
        else if (dso instanceof Item) 
        {
            // The dso may be an item but it could still be an item's bitstream. So let's check for the parameter.
            if (request.getParameter("bitstreamId") != null) {
                String identifier = "unknown";
                try {
                    Bitstream bit = Bitstream.find(context, new Integer(request.getParameter("bitstreamId")));
                    if (bit != null) {
                        identifier = bit.getName();
                    }
                } catch (Exception e) {
                    // just forget it - and display the restricted message.
                    log.trace("Caught exception", e);
                }
                unauthorized = body.addDivision("unauthorized-resource", "primary");
                unauthorized.setHead(T_head_bitstream);
                unauthorized.addPara(T_para_bitstream.parameterize(identifier));

            } else {

                String identifier = "unknown";
                String handle = dso.getHandle();
                if (handle == null || "".equals(handle)) {
                    identifier = "internal ID: " + dso.getID();
                } else {
                    identifier = "hdl:" + handle;
                }

                // check why the item is restricted.
                String divID = "restricted";
                Message title = T_head_item;
                Message status = T_para_item_restricted;
                Boolean isReplaced = false;
                String replacementUri = null;
                //if item is withdrawn, display withdrawn status info
                if (((Item) dso).isWithdrawn()) 
                {
                    divID = "withdrawn";
                    title = T_head_item_withdrawn;
                    status = T_para_item_withdrawn;
                    isWithdrawn = true;


                    // Check replacement configuration and metadata
                    Boolean replacedByEnabled = ConfigurationManager.getBooleanProperty("tombstone.replaced_by.enabled",true);
                    String replacedByField = ConfigurationManager.getProperty("tombstone.replaced_by.field");
                    if (replacedByField == null || replacedByField.equals("")) {
                        replacedByField = "dc.relation.isreplacedby";
                    }
                    if(replacedByEnabled) {
                        Metadatum[] replacedBy = ((Item)dso).getMetadataByMetadataString(replacedByField);
                        if (replacedBy != null && replacedBy.length > 0) {
                            for(Metadatum r : replacedBy) {
                                replacementUri = r.value;
                                if (replacementUri.length() > 0 && replacementUri.startsWith("http")) {
                                    isReplaced = true;
                                    break;
                                }
                                else if(handlePrefix != null && canonicalPrefix != null &&
                                        replacementUri.startsWith(handlePrefix)) {
                                    replacementUri = canonicalPrefix + handlePrefix;
                                    isReplaced = true;
                                    break;
                                }
                            }
                        }
                    }

                }//if user is not authenticated, display info to authenticate
                else if (context.getCurrentUser() == null) 
                {
                    status = T_para_item_restricted_auth;
                }
                unauthorized = body.addDivision(divID, "primary");
                unauthorized.setHead(title);

                // If withdrawn and replaced, display custom message with new URI
                // Only display "restricted item" message and status if not replaced
                if(isReplaced) {
                    Para replacement = unauthorized.addPara();
                    replacement.addContent(T_replaced_by);
                    replacement.addContent(" "); // Ensure a space between message and link
                    replacement.addXref(replacementUri).addContent(replacementUri);
                }
                else {
                    unauthorized.addPara(T_para_item.parameterize(identifier));
                    unauthorized.addPara("item_status", status.getKey()).addContent(status);
                }

            }
        } // end if Item 
        else 
        {
            // This case should not occur, but if it does just fall back to the resource message.
            unauthorized = body.addDivision("unauthorized-resource", "primary");
            unauthorized.setHead(T_head_resource);
            unauthorized.addPara(T_para_resource);
        }

        // add a login link if !loggedIn & not withdrawn
        if (!isWithdrawn && context.getCurrentUser() == null) 
        {
            unauthorized.addPara().addXref(contextPath+"/login", T_para_login);

            // Interrupt request if the user is not authenticated, so they may come back to
            // the restricted resource afterwards.
            String header = parameters.getParameter("header", null);
            String message = parameters.getParameter("message", null);
            String characters = parameters.getParameter("characters", null);

            // Interrupt this request
            AuthenticationUtil.interruptRequest(objectModel, header, message, characters);
        }
        
        //Finally, set proper response. Return "404 Not Found" for all restricted/withdrawn items
        HttpServletResponse response = (HttpServletResponse)objectModel
		.get(HttpEnvironment.HTTP_RESPONSE_OBJECT);   
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
}
