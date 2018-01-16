/*
 * This class is provided under Apache License, Version 2.0
 */

package st.theori.apps.ec2_knock_knock;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AmazonEC2Exception;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.RevokeSecurityGroupIngressResult;

/**
 * As we need this is more than one location in the code, we embody this in its own class as opposed inside the
 * 		lambda function like we do with authorization.
 */
class RevocationHelper {

	final AmazonEC2 ec2Instance;
	final Integer ingressPort;
	final String securityGroupId;

	RevocationHelper (final AmazonEC2 ec2, final int port, final String sgId) {
		this.ec2Instance = ec2;

		this.ingressPort = new Integer(port);

		this.securityGroupId = sgId;
	}

	RevokeSecurityGroupIngressResult performRevocationOnAddress (final String address)
			throws AmazonEC2Exception {
		final RevokeSecurityGroupIngressRequest revokeRequest = new RevokeSecurityGroupIngressRequest()
																			.withGroupId(this.securityGroupId)
																			.withFromPort(this.ingressPort)
																			.withToPort(this.ingressPort)
																			.withCidrIp(address + Maine.CIDR_SUFFIX)
																			.withIpProtocol(Maine.PROTOCOL);

		return this.ec2Instance.revokeSecurityGroupIngress(revokeRequest);
	}

}
