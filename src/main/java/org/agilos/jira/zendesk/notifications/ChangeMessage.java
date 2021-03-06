package org.agilos.jira.zendesk.notifications;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Representation of the change messages for Zendesk. Contains 2 parts, one message for attribute changes and one part for any comment. (the Zendesk interfaces can't handle all data in one request).
 * <p/>
 * The output of a <code>ChangeMessage</code> will be DOM objects representing the XML to send.
 *
 * @author mikis
 */
public class ChangeMessage {
    private static DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    private MessageParts message;
    private Node ticket;
    public static boolean publicComments = true;
    private String author;
    private String issueString;
    private StringBuffer changeString = new StringBuffer();
    private String jiraComment = null;
    private String changeComment = "";
    private String uploadToken;

    public ChangeMessage(String author, String jiraIssue) {
        this.author = author;
        this.issueString = jiraIssue;
        message = new MessageParts();
    }

    /**
     * Adds a change of a Zendesk mapped attribute to the change message
     *
     * @param zendeskFieldID The Zendesk attribute the JIRA field should be mapped to. If null the no attribute update element is added to the message,
     *                       eg. corresponds to the {@link #addChange(String, String, String)} method
     * @param jiraFieldID
     * @param newValue
     */
    public void addChange(String zendeskFieldID, String jiraFieldID, String newValue, String oldValue) {
        if (message.ticketChanges == null && zendeskFieldID != null) {
            try {
                message.ticketChanges = factory.newDocumentBuilder().getDOMImplementation().createDocument(null, null, null);
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }

            ticket = message.ticketChanges.createElement("ticket");
            message.ticketChanges.appendChild(ticket);
        }
        if (zendeskFieldID != null) {
            Element element = message.ticketChanges.createElement(zendeskFieldID);
            Text value = message.ticketChanges.createTextNode(newValue);
            element.appendChild(value);
            ticket.appendChild(element);
        }

        addChange(jiraFieldID, newValue, oldValue);
    }

    public void addChange(String jiraFieldID, String newValue, String oldValue) {
        changeString.append("\n" + capitalizeFirstLetter(jiraFieldID) + ": " + newValue);
        if (oldValue != null) {
            changeString.append(" (was: " + oldValue + ")");
        }
    }

    public void addComment(String comment) {
        jiraComment = comment;
    }

    public void addChangeComment(String comment) {
        changeComment = changeComment + "\n" + comment;
    }

    public void addUpload(String uploadToken) {
        this.uploadToken = uploadToken;
    }

    private void createComment(String commentString) throws DOMException, ParserConfigurationException {
        message.comment = factory.newDocumentBuilder().getDOMImplementation().createDocument(null, null, null);
        Element comment = message.comment.createElement("comment");

        Element isPublic = message.comment.createElement("is-public");
        if (publicComments) {
            isPublic.appendChild(message.comment.createTextNode("true"));
        } else {
            isPublic.appendChild(message.comment.createTextNode("false"));
        }
        comment.appendChild(isPublic);

        Element commentValue = message.comment.createElement("value");
        commentValue.appendChild(message.comment.createTextNode(commentString));
        comment.appendChild(commentValue);

        if (uploadToken != null) {
            Element tokenValue = message.comment.createElement("uploads");
            tokenValue.appendChild(message.comment.createTextNode(uploadToken));
            comment.appendChild(tokenValue);
        }

        message.comment.appendChild(comment);
    }

    public boolean isEmpty() {
        return (changeString.length() == 0 &&
                jiraComment == null);
    }

    public MessageParts createMessageParts() throws DOMException, ParserConfigurationException {
        StringBuffer commentSB = new StringBuffer();

        if (changeString.length() != 0) {
            commentSB.append(changeString + "\n");
        }

        if (changeComment.length() > 0) {
            commentSB.append(changeComment + "\n");
        }

        if (jiraComment != null) {
            commentSB.append("\nComment: " + jiraComment + "\n");
        }
        if (commentSB.length() > 0)
            createComment(author + " has updated JIRA issue " + issueString + " with:" + commentSB.toString());
        return message;
    }

    private String capitalizeFirstLetter(String inputWord) {
        String firstLetter = inputWord.substring(0, 1);  // Get first letter
        String remainder = inputWord.substring(1);    // Get remainder of word.
        return firstLetter.toUpperCase() + remainder.toLowerCase();
    }

    /**
     * Zendesk is not able to handle a message containing both comments and attribute changes, so this needs to be separated into 2 parts.
     */
    public static class MessageParts {
        Document ticketChanges;
        Document comment;

        /**
         * Returns the changes which should be made to the Zendesk ticket. Return null if no changes are present.
         *
         * @return
         */
        public String getTicketChanges() {
            return xmlToString(ticketChanges);
        }

        public boolean hasTicketChanges() {
            return ticketChanges != null;
        }

        /**
         * Returns the the comment which should be made added to the Zendesk ticket.
         *
         * @return
         */
        public String getComment() {
            return xmlToString(comment);
        }

        public boolean hasComment() {
            return comment != null;
        }


        private String xmlToString(Node node) {
            if (node == null) return null;
            try {
                Source source = new DOMSource(node);
                StringWriter stringWriter = new StringWriter();
                Result result = new StreamResult(stringWriter);
                TransformerFactory factory = TransformerFactory.newInstance();
                Transformer transformer = factory.newTransformer();
                transformer.transform(source, result);
                return stringWriter.getBuffer().toString();
            } catch (TransformerConfigurationException e) {
                e.printStackTrace();
            } catch (TransformerException e) {
                e.printStackTrace();
        }
        return null;
    }
	}
}
