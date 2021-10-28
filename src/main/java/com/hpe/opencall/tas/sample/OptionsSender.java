package com.hpe.opencall.tas.sample;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sip.ClientTransaction;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.ListeningPoint;
import javax.sip.PeerUnavailableException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AllowHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.javax.sip.Utils;

public class OptionsSender implements SipListener {

	private static Logger _log = LoggerFactory.getLogger(OptionsSender.class);

	private static String LISTENING_POINT_ADDRESS = "LISTENING_POINT_ADDRESS";
	private static String LISTENING_POINT_PORT = "LISTENING_POINT_PORT";
	private static String LISTENING_POINT_TRANSPORT = "LISTENING_POINT_TRANSPORT";

	private static String DEFAULT_LISTENING_POINT_ADDRESS = "0.0.0.0";
	private static String DEFAULT_LISTENING_POINT_PORT = "5060";
	private static String DEFAULT_LISTENING_POINT_TRANSPORT = "UDP";

	private static String DESTINATION_HOSTPORT = "DESTINATION_HOSTPORT";
	private static String ADVERTISED_HOST = "ADVERTISED_HOST";
	private static String ADVERTISED_PORT = "ADVERTISED_PORT";
	private static String DEFAULT_ADVERTISED_PORT = "5060";

	private final String address;
	private final int port;
	private final String transport;
	private final String destinationHostPort;
	private final String advertisedHost;
	private final int advertisedPort;

	private SipStack stack = null;
	private SipProvider provider = null;
	private ScheduledExecutorService executor;

	public OptionsSender(String address, int port, String transport, String destinationUri, String advertisedHost,
			int advertisedPort) {
		this.address = address;
		this.port = port;
		this.transport = transport;
		this.destinationHostPort = destinationUri;
		this.advertisedHost = advertisedHost;
		this.advertisedPort = advertisedPort;
	}

	void start() throws Exception {

		executor = Executors.newSingleThreadScheduledExecutor();
		createStack();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("->shutdownHook()");
			try {
				stack.stop();
				executor.shutdownNow();
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("<-shutdownHook()");
		}));
		
		executor.scheduleAtFixedRate(this::sendOptions, 1L, 1L, TimeUnit.SECONDS);

	}

	void createStack() throws Exception {

		javax.sip.SipFactory.getInstance().setPathName("gov.nist");
		SipFactory sipFactory = javax.sip.SipFactory.getInstance();
		sipFactory.createMessageFactory();
		sipFactory.createHeaderFactory();
		sipFactory.createAddressFactory();

		Properties props = new Properties();
		props.setProperty("javax.sip.STACK_NAME", "options-sender");

		stack = sipFactory.createSipStack(props);
		_log.info("SIP Stack created - " + stack);
		ListeningPoint listeningPoint = stack.createListeningPoint(address, port, transport);
		provider = stack.createSipProvider(listeningPoint);
		_log.info("SIP provider created - " + provider);
		provider.addSipListener(this);

	}

	@SuppressWarnings("unchecked")
	void sendOptions() {

		try {
		SipFactory sipFactory = javax.sip.SipFactory.getInstance();
		MessageFactory messageFactory = sipFactory.createMessageFactory();
		HeaderFactory headerFactory = sipFactory.createHeaderFactory();
		AddressFactory addressFactory = sipFactory.createAddressFactory();

		String fromSipAddress = "options-sender.com";
		String toSipAddress = "options-responder.com";

		String localTag = Utils.getInstance().generateTag();

		// create From Header
		SipURI fromAddress = addressFactory.createSipURI(null, fromSipAddress);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, localTag);

		// create To Header
		SipURI toAddress = addressFactory.createSipURI(null, toSipAddress);
		Address toNameAddress = addressFactory.createAddress(toAddress);
		ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);

		// create Request URI
		String peerHostPort = destinationHostPort;
		SipURI requestURI = addressFactory.createSipURI(null, peerHostPort);
		requestURI.setTransportParam(this.transport);

		// Create ViaHeaders
		@SuppressWarnings("rawtypes")
		ArrayList viaHeaders = new ArrayList();
		_log.debug("Via header host: " + this.advertisedHost + ", port: " + this.advertisedPort + ", transport: "
				+ this.transport);
		ViaHeader viaHeader = headerFactory.createViaHeader(advertisedHost, advertisedPort, transport, null);

		// add via headers
		viaHeaders.add(viaHeader);

		// Create a new CallId header
		CallIdHeader callIdHeader = provider.getNewCallId();

		// Create a new Cseq header
		CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.OPTIONS);

		// Create a new MaxForwardsHeader
		MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

		// Create contact headers
		SipURI contactUrl = addressFactory.createSipURI(null, advertisedHost);
		contactUrl.setPort(advertisedPort);
		Address contactAddress = addressFactory.createAddress(contactUrl);
		ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);

		// Create the request.
		Request request = messageFactory.createRequest(requestURI, Request.OPTIONS, callIdHeader, cSeqHeader, fromHeader,
				toHeader, viaHeaders, maxForwards);
		request.addHeader(contactHeader);
		
		// Create the client transaction.
        ClientTransaction transaction = provider.getNewClientTransaction(request);
        
        // Send the request.
        transaction.sendRequest();
		} catch (Exception e) {
			_log.error("Failed to send OPTIONS: " + e.getMessage(), e);
			System.err.println("Failed to send OPTIONS: " + e.getMessage());
		}
	}

	public static void main(String[] args) {

		String listeningPointAddress = System.getenv(LISTENING_POINT_ADDRESS);
		if (null == listeningPointAddress) {
			listeningPointAddress = DEFAULT_LISTENING_POINT_ADDRESS;
		}
		String listeningPointPortAsStr = System.getenv(LISTENING_POINT_PORT);
		if (null == listeningPointPortAsStr) {
			listeningPointPortAsStr = DEFAULT_LISTENING_POINT_PORT;
		}
		int listeningPointPort;
		try {
			listeningPointPort = Integer.parseUnsignedInt(listeningPointPortAsStr);
		} catch (NumberFormatException e) {
			System.err.println("Failed to parse port = " + e.getMessage());
			return;
		}
		String listeningPointTransport = System.getenv(LISTENING_POINT_TRANSPORT);
		if (null == listeningPointTransport) {
			listeningPointTransport = DEFAULT_LISTENING_POINT_TRANSPORT;
		}

		String destination = System.getenv(DESTINATION_HOSTPORT);
		if (null == destination) {
			System.err.println("No value for " + DESTINATION_HOSTPORT + " environment variable.");
			System.exit(1);
		}

		String advertisedHost = System.getenv(ADVERTISED_HOST);
		if (null == advertisedHost) {
			System.err.println("No value for " + ADVERTISED_HOST + " environment variable.");
			System.exit(1);
		}

		String advertisedPortAsStr = System.getenv(ADVERTISED_PORT);
		if (null == advertisedPortAsStr) {
			advertisedPortAsStr = DEFAULT_ADVERTISED_PORT;
		}
		int advertisedPort;
		try {
			advertisedPort = Integer.parseUnsignedInt(advertisedPortAsStr);
		} catch (NumberFormatException e) {
			System.err.println("Failed to parse advertised port = " + e.getMessage());
			return;
		}

		OptionsSender optionsSender = new OptionsSender(listeningPointAddress, listeningPointPort,
				listeningPointTransport, destination, advertisedHost, advertisedPort);
		try {
			optionsSender.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

	}

	@Override
	public void processDialogTerminated(DialogTerminatedEvent arg0) {
		_log.info("Received dialog terminated event - " + arg0.toString());
	}

	@Override
	public void processIOException(IOExceptionEvent arg0) {
		_log.info("Received I/O error event - " + arg0.toString());
	}

	@Override
	public void processRequest(RequestEvent ev) {

		javax.sip.message.Request req = ev.getRequest();
		String method = req.getMethod();
		if (_log.isDebugEnabled())
			_log.debug("Received incoming message:\n" + req);
		ServerTransaction tx = ev.getServerTransaction();

		try {
			if (tx == null) {
				if (_log.isDebugEnabled())
					_log.debug("Transaction is null, creating new server transaction...");
				tx = ((javax.sip.SipProvider) ev.getSource()).getNewServerTransaction(ev.getRequest());
				if (_log.isDebugEnabled())
					_log.debug("New server transaction created");
			} else {
				if (_log.isDebugEnabled())
					_log.debug("Server transaction exists");
			}
		} catch (Exception ex) {
			_log.error("cannot create/find txn for incoming request: " + ex);
			return;
		}

		MessageFactory messageFactory;
		HeaderFactory headerFactory;
		try {
			messageFactory = SipFactory.getInstance().createMessageFactory();
			headerFactory = SipFactory.getInstance().createHeaderFactory();
		} catch (PeerUnavailableException e) {
			_log.error("Failed to get SIP message factory - " + e.getMessage(), e);
			return;
		}

		if (method.equalsIgnoreCase("OPTIONS")) {
			// Respond with 200 OK.
			try {
				Response okResponse = messageFactory.createResponse(Response.OK, req);
				AllowHeader allowHeader = headerFactory.createAllowHeader(Request.OPTIONS);
				okResponse.addHeader(allowHeader);
				tx.sendResponse(okResponse);
			} catch (Exception ex) {
				_log.error("Failed to send reject response - " + ex.getMessage(), ex);
			}

		} else {
			if (method.equalsIgnoreCase(Request.ACK)) {
				// NOP.
				return;
			}
			// Reject all other methods.
			try {
				Response rejectResponse = messageFactory.createResponse(Response.METHOD_NOT_ALLOWED, req);
				AllowHeader allowHeader = headerFactory.createAllowHeader(Request.OPTIONS);
				rejectResponse.addHeader(allowHeader);
				tx.sendResponse(rejectResponse);
			} catch (Exception ex) {
				_log.error("Failed to send reject response - " + ex.getMessage(), ex);
			}
		}

	}

	@Override
	public void processResponse(ResponseEvent ev) {
		Response resp = ev.getResponse();
		int code = resp.getStatusCode();
		CSeqHeader cseq = (CSeqHeader) resp.getHeader(CSeqHeader.NAME);
		if (_log.isDebugEnabled())
			_log.debug("Response Code = " + code + ", method = " + cseq.getMethod());
		if (_log.isDebugEnabled())
			_log.debug("Reason = " + resp.getReasonPhrase());

		if(Request.OPTIONS.equalsIgnoreCase(cseq.getMethod())) {
			_log.info("Received OPTIONS response " + resp.getStatusCode());
			System.out.println("Received OPTIONS response " + resp.getStatusCode());
		}
		return;
	}

	@Override
	public void processTimeout(TimeoutEvent arg0) {
		_log.info("Received timeout event - " + arg0.toString());
		System.out.println("Received timeout event - " + arg0.toString());

	}

	@Override
	public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
		_log.info("Received transaction terminated event - " + arg0.toString());
	}
}
