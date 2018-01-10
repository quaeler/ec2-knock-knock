/*
 * This class is provided under Apache License, Version 2.0
 */

package st.theori.apps.ec2_knock_knock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressResult;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult;

import spark.Spark;

/**
 * A simple knock-knock-and-goodbye server for EC2 instances.
 *
 * 	https://github.com/quaeler/ec2-knock-knock
 *
 * Future TODO: time-out removal of IPs knock-knock'd in.
 */
public class Maine {

	static protected final Logger LOGGER = LoggerFactory.getLogger(Maine.class);

	static protected final String CIDR_SUFFIX = "/32";

	static protected void printUsageAndExit () {
		System.out.println("java -jar ... _listen_port_ _knock_relative_url_ _good_bye_relative_url_ _security_group_id_");

		System.exit(1);
	}

	static public void main (final String[] args) {
		final AmazonEC2 ec2;

		if (args.length != 4) {
			Maine.printUsageAndExit();
		}

		// If an instance is not thread-safe, we need re-visit this; i've not see any documentation stating such,
		//		so we are sharing the instance.
		ec2 = AmazonEC2ClientBuilder.defaultClient();

		Spark.port(Integer.parseInt(args[0]));


		// knock in
		Spark.get(args[1], (request, response) -> {
			String address = request.ip();
			final AuthorizeSecurityGroupIngressRequest authorizeRequest = new AuthorizeSecurityGroupIngressRequest()
																				.withGroupId(args[3])
																				.withFromPort(22)
																				.withToPort(22)
																				.withCidrIp(address + CIDR_SUFFIX)
																				.withIpProtocol("tcp");

			try {
				final AuthorizeSecurityGroupIngressResult result = ec2.authorizeSecurityGroupIngress(authorizeRequest);

				LOGGER.info("Received knock-knock request - have authorized ingress for {}", address);

				return "Hello " + address;
			}
			catch (AmazonEC2Exception e) {
				String msg = e.getMessage();

				LOGGER.error("Exception encountered during knock-knock for " + address, e);

				if (msg != null) {
					msg = msg.replaceAll(args[3], "sg-XXXXXXXX");
				}
				else {
					msg = "No exception message exists.";
				}

				return "Failed Hello " + address + " -- " + msg;
			}
		});


		// knock out
		Spark.get(args[2], (request, response) -> {
			final String address = request.ip();
			final RevokeSecurityGroupIngressRequest revokeRequest = new RevokeSecurityGroupIngressRequest()
																			.withGroupId(args[3])
																			.withFromPort(22)
																			.withToPort(22)
																			.withCidrIp(address + CIDR_SUFFIX)
																			.withIpProtocol("tcp");

			try {
				final RevokeSecurityGroupIngressResult result = ec2.revokeSecurityGroupIngress(revokeRequest);

				LOGGER.info("Received goodbye request - have revoked ingress for {}", address);

				return "Goodbye " + address;
			}
			catch (AmazonEC2Exception e) {
				String msg = e.getMessage();

				LOGGER.error("Exception encountered during goodbye for " + address, e);

				if (msg != null) {
					msg = msg.replaceAll(args[3], "sg-XXXXXXXX");
				}
				else {
					msg = "No exception message exists.";
				}

				return "Failed Goodbye " + address + " -- " + msg;
			}
		});

		LOGGER.info("For security group id {}, we are authorizing ingresses on {} and revoking on {}",
					args[3], args[1], args[2]);
	}

}
