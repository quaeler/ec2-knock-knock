# ec2-knock-knock
A Knock Knock server for EC2 instances.

A Spark based server which listens on a user specified port for user specified URLs for SSH ingress authorizations (knock-knock) and revocations (departure) to a user specified EC2 security group.

## Building

This project is Java 8 dependent; otherwise build the normal maven `package` target to end up with a shaded main-class'd jar in your local target directory.

## Running

The application requires 4 arguments; running with more or less than this will dump usage to stdout and exit.

The 4 arguments, in order, are:
1. port to listen on
2. knock-knock relative URL path
3. departure relative URL path
4. EC2 security group id

For example, were this running on an EC2 instance at the public IP of 1.2.3.4 associated to a security group with id sg-ffffff, then invoking the application like this:
```shell
java -jar ec2-knock-knock.jar 19181 /will/not/guess/me /me/either sg-ffffff
```
would mean that visiting `http://1.2.3.4:19181/will/not/guess/me` from a web browser will allow ingress to your EC2 instance on port 22 from IP making the HTTP request.
Similarly, visiting `http://1.2.3.4:19181/me/either` will revoke the ingress rule.

If you wish to have systemctl manage running this, feel free to modify the .service file included in the repository.

## To-Do-s

* Authorizations should be tracked, backed by a database, and timed-out after some user specified time period.
