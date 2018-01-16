/*
 * This class is provided under Apache License, Version 2.0
 */

package st.theori.apps.ec2_knock_knock;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;
import org.hibernate.type.StringType;
import org.hibernate.type.TimestampType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is both a DAO and a tender to the session expirations; were we writing this with abstraction in
 * 	heart, we'd separate out the specific backing store mechanisms and keep this pluggable. As i'm content
 * 	at this pass to tightly couple to H2 + Hibernate, consider that a FUTURISM.
 */
class PersistenceStoreTender {

	static final long RUNNABLE_SLEEP = TimeUnit.SECONDS.toMillis(20);

	static private final Logger LOGGER = LoggerFactory.getLogger(PersistenceStoreTender.class);

	static private final String EXPIRATION_QUERY
				= "FROM IngressSession"
				  	+ " WHERE (revocationDate IS NULL)"
				  			+ " AND (expirationDate < CURRENT_TIMESTAMP())";
	static private final String OPEN_FOR_IP_QUERY
				= "FROM IngressSession"
				  	+ " WHERE (revocationDate IS NULL)"
				  			+ " AND (ipAddress = :ipAddress)";
	static private final String REVOCATION_UPDATE
				= "UPDATE IngressSession"
					+ " SET revocationDate = :revokeDateTime"
					+ " WHERE id = :rowId";


	final RevocationHelper revocationHelper;
	final long expirationMS;

	final SessionFactory sessionFactory;

	PersistenceStoreTender (final RevocationHelper rr, final String databaseFile, final int expiration) {
		this.revocationHelper = rr;

		this.expirationMS = TimeUnit.MINUTES.toMillis(expiration);

		this.sessionFactory = this.connectToDatabase(databaseFile);

		this.logTableInformation(true);

		Thread t = new Thread(new ExpirationRunnable());
		t.setDaemon(true);
		t.start();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			PersistenceStoreTender.this.sessionFactory.close();
		}));
	}

	final SessionFactory connectToDatabase (final String databaseFile)
			throws IllegalStateException {
		try {
			final String dbURL = "jdbc:h2:" + databaseFile;
			final Configuration configuration = new Configuration();
			final SessionFactory rhett;

			// Since we can't specify all the configuration in a standard cfg.xml file, it feels more gross to mix
			//		definitions in two places than have this laundry list of code in one place.
			configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
			configuration.setProperty("hibernate.connection.url", dbURL);
			configuration.setProperty("hibernate.connection.username", "sa");
			configuration.setProperty("hibernate.connection.password", "");

			configuration.setProperty("hibernate.hbm2ddl.auto", "update");

			configuration.setProperty("hibernate.show_sql", "false");
			configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect");

			configuration.setProperty("hibernate.connection.provider_class",
									  "org.hibernate.connection.C3P0ConnectionProvider");
			configuration.setProperty("hibernate.c3p0.acquire_increment", "2");
			configuration.setProperty("hibernate.c3p0.idle_test_period", "30");
			configuration.setProperty("hibernate.c3p0.timeout", "60");
			configuration.setProperty("hibernate.c3p0.min_size", "3");
			configuration.setProperty("hibernate.c3p0.max_size", "60");
			configuration.setProperty("hibernate.c3p0.max_statements", "25");	// basically moot given our scope
			configuration.setProperty("hibernate.c3p0.acquireRetryAttempts", "1");
			configuration.setProperty("hibernate.c3p0.acquireRetryDelay", "317");

			configuration.addAnnotatedClass(IngressSession.class);

			rhett = configuration.buildSessionFactory();

			LOGGER.debug("Hibernate session factory created.");

			return rhett;
		}
		catch (Exception e) {
			LOGGER.error("Exception caught attempting to spin up Hibernate session.", e);

			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")   // Generics casting...
	final void logTableInformation (boolean isStartup) {
		final Session s = this.sessionFactory.openSession();
		final int totalCount;
		int openCount;
		Query<?> q;

		try {
			s.beginTransaction();

			q = s.createQuery("SELECT COUNT(*) FROM IngressSession");
			totalCount = ((Long)q.iterate().next()).intValue();

			if (isStartup) {
				final List<String> is;

				q = s.createQuery("SELECT ipAddress FROM IngressSession WHERE revocationDate IS NULL", String.class);
				is = (List<String>)q.list();

				if (is.size() > 0) {
					LOGGER.debug("Session tracking has {} total sessions with {} still open: {}", totalCount, is.size(),
								 String.join(", ", is));
				}
				else {
					LOGGER.debug("Session tracking has {} total sessions with 0 still open.", totalCount);
				}
			}
			else {
				q = s.createQuery("SELECT COUNT(*) FROM IngressSession WHERE revocationDate IS NULL");

				LOGGER.debug("Session tracking has {} total sessions with {} still open.", totalCount,
							 ((Long)q.iterate().next()).intValue());
			}
		}
		finally {
			s.close();
		}
	}

	Date storeSuccessfulAuthorization (String address) {
		final Session s = this.sessionFactory.openSession();
		final Date now = new Date();
		final Date expire = new Date(now.getTime() + this.expirationMS);
		final IngressSession is = (new IngressSession()).setIpAddress(address)
														.setAuthorizationDate(now)
														.setExpirationDate(expire);
		Transaction t = null;

		try {
			t = s.beginTransaction();

			s.save(is);

			t.commit();

			return expire;
		}
		catch (Exception e) {
			if (t != null) {
				t.rollback();
			}

			return null;
		}
		finally {
			s.close();
		}
	}

	void storeSuccessfulRevocation (String address) {
		final Session s = this.sessionFactory.openSession();
		Transaction t = null;

		try {
			final Query<IngressSession> q;
			final List<IngressSession> is;

			t = s.beginTransaction();

			q = s.createQuery(OPEN_FOR_IP_QUERY, IngressSession.class).setParameter("ipAddress", address,
																					StringType.INSTANCE);
			is = q.list();

			if (is.size() == 0) {
				LOGGER.warn("We can find no open session for ip {}!", address);
			}
			else {
				final Date revokeDate = new Date();
				Query<?> query;

				if (is.size() > 1) {
					LOGGER.warn("We're expiring more than one matching open session for ip {}!", address);
				}

				for (IngressSession ingressSession : is) {
					query = s.createQuery(REVOCATION_UPDATE)
							 .setParameter("revokeDateTime", revokeDate, TimestampType.INSTANCE)
							 .setParameter("rowId", ingressSession.getId());

					query.executeUpdate();
				}
			}
		}
		finally {
			// Store what we've succeeded to get in there without exception in the should-never-happen multiple case.
			if (t != null) {
				t.commit();
			}

			s.close();
		}
	}

	// In this scenario, it's safe to consider that the current_timestamp in the database's context is
	//		our current time as well.
	List<IngressSession> getExpiredOpenSessions () {
		final Session s = this.sessionFactory.openSession();
		final Query<IngressSession> q = s.createQuery(EXPIRATION_QUERY, IngressSession.class);

		s.beginTransaction();

		return q.list();
	}


	protected class ExpirationRunnable
			implements Runnable {

		public void run () {
			final PersistenceStoreTender outer = PersistenceStoreTender.this;
			final int logSpewWaitCount = (int)(TimeUnit.MINUTES.toMillis(1) / RUNNABLE_SLEEP);
			List<IngressSession> expiredSessions;
			int sleepCount = 0;

			LOGGER.info("Expiration runnable started.");

			while (true) {
				try {
					Thread.sleep(RUNNABLE_SLEEP);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();

					LOGGER.info("Expiration runnable interrupted - exiting.");

					return;
				}

				expiredSessions = outer.getExpiredOpenSessions();
				for (IngressSession expiredSession : expiredSessions) {
					outer.revocationHelper.performRevocationOnAddress(expiredSession.getIpAddress());
					outer.storeSuccessfulRevocation(expiredSession.getIpAddress());

					LOGGER.info("Expired session for IP {}", expiredSession.getIpAddress());
				}

				if (sleepCount < logSpewWaitCount) {
					sleepCount++;
				}
				else {
					outer.logTableInformation(false);

					sleepCount = 0;
				}
			}
		}

	}

}
