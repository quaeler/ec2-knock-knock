/*
 * This class is provided under Apache License, Version 2.0
 */

package st.theori.apps.ec2_knock_knock;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult;

import spark.Spark;

/**
 * A knock-knock-and-goodbye server for EC2 instances.
 *
 * 	https://github.com/quaeler/ec2-knock-knock
 *
 * The Amazon SDK API pages for the existing concrete implementations of AmazonEC2 (AmazonEC2AsyncClient
 * 	and AmazonEC2Client) list thread-safe annotations for these classes, so i am assuming we can treat
 * 	our AmazonEC2 instance as thread-safe.
 */
public final class Maine {

	static final String CIDR_SUFFIX = "/32";
	static final String PROTOCOL = "tcp";

	// Until there's a real release process, keep name and version hard coded here
	static private final String APP_NAME = "EC2 Knock Knock Server";
	static private final String APP_VERSION = "1.0.0";

	static private final String EXPIRATION_OPTION = "expiration";
	static private final String INGRESS_PORT_OPTION = "ingressPort";
	static private final String LISTEN_PORT_OPTION = "listenPort";

	static private final String DATABASE_FILE_OPTION = "dbFile";
	static private final String SECURITY_GROUP_ID_OPTION = "sgId";
	static private final String URL_OPTION = "url";

	static private final int DEFAULT_EXPIRATION = 30;
	static private final int DEFAULT_INGRESS_PORT = 22;
	static private final int DEFAULT_LISTEN_PORT = 11235;

	static private final Logger LOGGER = LoggerFactory.getLogger(Maine.class);
	static private final DateFormat DATE_FORMAT = new SimpleDateFormat("MMM d yyyy hh:mm a zzz");

	static private final String REVOCATION_URL_SUFFIX = "/bye";

	static private Options buildApplicationOptions () {
		final Options rhett = new Options();
		Option o;

		o = Option.builder(INGRESS_PORT_OPTION)
				  .required(false)
				  .hasArg()
				  .desc("If specified, this value will be the port authorized and revoked in the security group; if "
								+ "not specified, " + DEFAULT_INGRESS_PORT
								+ " will be used. This must be a positive and valid value.")
				  .build();
		rhett.addOption(o);

		o = Option.builder(LISTEN_PORT_OPTION)
				  .required(false)
				  .hasArg()
				  .desc("If specified, the server will listen on this port; if not specified, " + DEFAULT_LISTEN_PORT
								+ " will be used. This must be a positive and valid value.")
				  .build();
		rhett.addOption(o);

		o = Option.builder(EXPIRATION_OPTION)
				  .required(false)
				  .hasArg()
				  .desc("If specified, the ingress rule will be revoked after this many minutes; if not specified "
					   		+ DEFAULT_EXPIRATION + " will be used. This must be a positive value.")
				  .build();
		rhett.addOption(o);

		o = Option.builder(DATABASE_FILE_OPTION)
				  .required()
				  .hasArg()
				  .desc("This specifies the absolute path to the database file used for tracking ingress requests. "
					   		+ "If this file does not exist, it will be created; if it can not be created, the server "
					   		+ "will exit.")
				  .build();
		rhett.addOption(o);

		o = Option.builder(SECURITY_GROUP_ID_OPTION)
				  .required()
				  .hasArg()
				  .desc("This specifies the id of the EC2 security group which will be altered by the server requests.")
				  .build();
		rhett.addOption(o);

		o = Option.builder(URL_OPTION)
				  .required()
				  .hasArg()
				  .desc("This specifies the relative URL on which the server listens for ingress authorization "
						+ "requests; this URL suffixed with \"" + REVOCATION_URL_SUFFIX
						+ "\" will be used for ingress revocation requests.")
				  .build();
		rhett.addOption(o);

		return rhett;
	}

	static private void displayUsageAndExit (final Options options) {
		final HelpFormatter hf = new HelpFormatter();
		final String usageHeader
				= "\nRuns the EC2 Knock Knock server which listens for ingress authorization and revocation "
									+ "requests which arrive on a specified URL at a specified port.\n\n"
									+ "Revocations that are not explicitly made will be made automatically after an "
									+ "expiration time.\n\n";
		final String usageFooter = "\n" + APP_NAME + " v" + APP_VERSION;

		hf.setOptionComparator((Option o1, Option o2) -> {
			if (! o1.isRequired()) {
				return Integer.MIN_VALUE;
			}

			if (! o2.isRequired()) {
				return Integer.MAX_VALUE;
			}

			return 0;
		});

		hf.printHelp("java -jar ... ", usageHeader, options, usageFooter, true);

		System.exit(1);
	}

	static private int getOptionValue (final CommandLine cl, final String option, final String warnMessage,
									   final int defaultValue) {
		final String valueString = cl.getOptionValue(option);

		if (StringUtils.isNotBlank(valueString)) {
			try {
				return Integer.parseInt(valueString);
			}
			catch (NumberFormatException e) {
				LOGGER.warn(warnMessage);
			}
		}

		return defaultValue;
	}


	static public void main (final String[] args) {
		final Options options = Maine.buildApplicationOptions();

		if (args.length == 0) {
			Maine.displayUsageAndExit(options);
		}

		try {
			final DefaultParser dp = new DefaultParser();
			final CommandLine cl = dp.parse(options, args);
			final String dbFile = cl.getOptionValue(DATABASE_FILE_OPTION);
			final String rootURL = cl.getOptionValue(URL_OPTION);
			final String sgId = cl.getOptionValue(SECURITY_GROUP_ID_OPTION);
			final PersistenceStoreTender persistenceStoreTender;
			final RevocationHelper revocationHelper;
			final String expirationString;
			final String portString;
			final String byeURL;
			final AmazonEC2 ec2;
			final int expiration;
			final int ingressPort;
			final int bindPort;

			if (StringUtils.isBlank(rootURL) || StringUtils.isBlank(sgId) || StringUtils.isBlank(dbFile)) {
				Maine.displayUsageAndExit(options);
			}

			byeURL = rootURL + REVOCATION_URL_SUFFIX;

			expiration = Maine.getOptionValue(cl, EXPIRATION_OPTION,
											  "Could not parse specified expiration value - using the default "
											  		+ "value of " + DEFAULT_EXPIRATION + " instead.",
											  DEFAULT_EXPIRATION);
			ingressPort = Maine.getOptionValue(cl, INGRESS_PORT_OPTION,
											"Could not parse specified ingress port value - using the default "
													+ "value of " + DEFAULT_INGRESS_PORT + " instead.",
											   DEFAULT_INGRESS_PORT);
			bindPort = Maine.getOptionValue(cl, LISTEN_PORT_OPTION,
										"Could not parse specified listen port value - using the default "
												+ "value of " + DEFAULT_LISTEN_PORT + " instead.",
											DEFAULT_LISTEN_PORT);

			ec2 = AmazonEC2ClientBuilder.defaultClient();

			revocationHelper = new RevocationHelper(ec2, ingressPort, sgId);
			persistenceStoreTender = new PersistenceStoreTender(revocationHelper, dbFile, expiration);

			Spark.port(bindPort);

			// authorize
			Spark.get(rootURL, (request, response) -> {
				String address = request.ip();
				final AuthorizeSecurityGroupIngressRequest authorizeRequest = new AuthorizeSecurityGroupIngressRequest()
																					.withGroupId(sgId)
																					.withFromPort(ingressPort)
																					.withToPort(ingressPort)
																					.withCidrIp(address + CIDR_SUFFIX)
																					.withIpProtocol(PROTOCOL);

				try {
					final AuthorizeSecurityGroupIngressResult result = ec2.authorizeSecurityGroupIngress(authorizeRequest);
					final Date expirationDate;

					LOGGER.info("Received knock-knock request - have authorized ingress for {}", address);

					expirationDate = persistenceStoreTender.storeSuccessfulAuthorization(address);

					if (expirationDate != null) {
						return "Hello " + address + " your session will expire at " + DATE_FORMAT.format(expirationDate);
					}
					else {
						return "Hello " + address + " -- !! we have failed to track your session in the database, "
									+ " when finished, please explicitly close your session the URL: " + byeURL;
					}
				}
				catch (AmazonEC2Exception e) {
					String msg = e.getMessage();

					LOGGER.error("Exception encountered during knock-knock for {} with message {}", address, msg);

					if (msg != null) {
						msg = msg.replaceAll(sgId, "sg-XXXXXXXX");
					}
					else {
						msg = "No exception message exists.";
					}

					return "Failed Hello " + address + " -- " + msg;
				}
			});


			// revoke
			Spark.get(byeURL, (request, response) -> {
				final String address = request.ip();

				try {
					final RevokeSecurityGroupIngressResult result = revocationHelper.performRevocationOnAddress(address);

					LOGGER.info("Received goodbye request - have revoked ingress for {}", address);

					persistenceStoreTender.storeSuccessfulRevocation(address);

					return "Goodbye " + address;
				}
				catch (AmazonEC2Exception e) {
					String msg = e.getMessage();

					LOGGER.error("Exception encountered during goodbye for {} with message {}", address, msg);

					if (msg != null) {
						msg = msg.replaceAll(sgId, "sg-XXXXXXXX");
					}
					else {
						msg = "No exception message exists.";
					}

					return "Failed Goodbye " + address + " -- " + msg;
				}
			});


			LOGGER.info("For security group id {}, we are authorizing ingresses on {} and revoking on {}. Session expiration is {} minutes.",
						sgId, rootURL, byeURL, Integer.toString(expiration));
		}
		catch (Exception e) {
			LOGGER.error("Failed to start knock knock server.", e);

			System.exit(-1);
		}
	}

}
