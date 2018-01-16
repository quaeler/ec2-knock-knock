/*
 * This class is provided under Apache License, Version 2.0
 */

package st.theori.apps.ec2_knock_knock;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 * This is the annotated model class for the 'ingress session' table.
 */
@Entity
@Table(name = IngressSession.TABLE_NAME)
public class IngressSession {

	static final String TABLE_NAME = "INGRESS_SESSION";

	static final String ADDRESS_COLUMN_NAME = "IP_ADDRESS";
	static final String AUTHORIZATION_COLUMN_NAME = "AUTHORIZATION_DATE";
	static final String EXPIRATION_COLUMN_NAME = "EXPIRATION_DATE";
	static final String ID_COLUMN_NAME = "ID";
	static final String REVOCATION_COLUMN_NAME = "REVOCATION_DATE";


	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@Column(name = IngressSession.ID_COLUMN_NAME, updatable = false, nullable = false)
	private Long id;

	@Column(name = IngressSession.ADDRESS_COLUMN_NAME, updatable = false, nullable = false)
	private String ipAddress;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = IngressSession.AUTHORIZATION_COLUMN_NAME, updatable = false, nullable = false)
	private Date authorizationDate;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = IngressSession.EXPIRATION_COLUMN_NAME, updatable = false, nullable = false)
	private Date expirationDate;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = IngressSession.REVOCATION_COLUMN_NAME)
	private Date revocationDate;

	public Long getId () {
		return this.id;
	}

	public String getIpAddress () {
		return this.ipAddress;
	}

	public IngressSession setIpAddress (String address) {
		this.ipAddress = address;

		return this;
	}

	public Date getAuthorizationDate () {
		return this.authorizationDate;
	}

	public IngressSession setAuthorizationDate (Date date) {
		this.authorizationDate = date;

		return this;
	}

	public Date getExpirationDate () {
		return this.expirationDate;
	}

	public IngressSession setExpirationDate (Date date) {
		this.expirationDate = date;

		return this;
	}

	public Date getRevocationDate () {
		return this.revocationDate;
	}

	public IngressSession setRevocationDate (Date date) {
		this.revocationDate = date;

		return this;
	}

}
