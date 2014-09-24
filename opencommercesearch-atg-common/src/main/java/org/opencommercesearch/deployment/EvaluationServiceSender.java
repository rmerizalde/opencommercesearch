package org.opencommercesearch.deployment;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import atg.dms.patchbay.MessageSource;
import atg.dms.patchbay.MessageSourceContext;
import atg.nucleus.GenericService;

public class EvaluationServiceSender extends GenericService implements MessageSource{

	MessageSourceContext mMessageSourceContext;
	private boolean mMessageSourceActive;
	
	@Override
	public void setMessageSourceContext(MessageSourceContext pMessageSourceContext) {
		// TODO Auto-generated method stub
		mMessageSourceContext = pMessageSourceContext;
	}

	public MessageSourceContext getMessageSourceContext() {
        return mMessageSourceContext;
    }

	@Override
	public void startMessageSource() {
		// TODO Auto-generated method stub
		mMessageSourceActive = true;
	}

	@Override
	public void stopMessageSource() {
		// TODO Auto-generated method stub
		mMessageSourceActive = false;
	}
	
	public boolean isMessageSourceActive() {
        return mMessageSourceActive;
    }
	
	public void setMessageSourceActive(boolean mMessageSourceActive) {
	        this.mMessageSourceActive = mMessageSourceActive;
	}

	public void sendMessage(String status) {
        if (!isMessageSourceActive()) {
            if (isLoggingWarning()) {
                logWarning("Message source is not yet available, cannot send message");
            }
            return;
        }

        try {
            MessageSourceContext messageSourceContext = getMessageSourceContext();
            TextMessage textMessage = messageSourceContext.createTextMessage();
            textMessage.setText(status);
            textMessage.setJMSType("javax.jms.TextMessage");
            if (isLoggingDebug()) {
                logDebug("Sending message: " + textMessage.toString());
                logDebug("Message JMSType: " + textMessage.getJMSType());
            }
            messageSourceContext.sendMessage(textMessage);
        } catch (JMSException je) {
            if (isLoggingError()) {
                logError(je);
            }
        }
    }


}