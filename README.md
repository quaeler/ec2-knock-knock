# ec2-knock-knock
A Spark based server which listens on a user specified port for user specified URLs for ingress authorizations (knock-knock) and revocations (departure) to a user specified EC2 security group, on an optionally specified ingress-port (defaults to 22) with a specify-able expiration time-out should the requesting user not remember to revoke their ingress.

## Building

This project is Java 8 dependent; build the normal maven `package` target to end up with a shaded main-class'd jar in your local target directory.

## Running

Executing the application with no arguments shows the usage and exits.

```text
usage: java -jar ...  [-expiration <arg>] [-listenPort <arg>]
       [-ingressPort <arg>] -dbFile <arg> -sgId <arg> -url <arg>

Runs the EC2 Knock Knock server which listens for ingress authorization
and revocation requests which arrive on a specified URL at a specified
port.

Revocations that are not explicitly made will be made automatically after
an expiration time.

 -expiration <arg>    If specified, the ingress rule will be revoked after
                      this many minutes; if not specified 30 will be used.
                      This must be a positive value.
 -listenPort <arg>    If specified, the server will listen on this port;
                      if not specified, 11235 will be used. This must be a
                      positive and valid value.
 -ingressPort <arg>   If specified, this value will be the port authorized
                      and revoked in the security group; if not specified,
                      22 will be used. This must be a positive and valid
                      value.
 -dbFile <arg>        This specifies the absolute path to the database
                      file used for tracking ingress requests. If this
                      file does not exist, it will be created; if it can
                      not be created, the server will exit.
 -sgId <arg>          This specifies the id of the EC2 security group
                      which will be altered by the server requests.
 -url <arg>           This specifies the relative URL on which the server
                      listens for ingress authorization requests; this URL
                      suffixed with "/bye" will be used for ingress
                      revocation requests.

EC2 Knock Knock Server v1.0.0
```

For example, were this running on an EC2 instance at the public IP of 1.2.3.4 associated to a security group with id sg-ffffff, then invoking the application like this:
```shell
java -jar target/ec2-knock-knock.jar -listenPort 19181 -ingressPort 24 -dbFile /usr/local/etc/ec2kkdb -sgId sg-ffffff -url /will/not/guess/me
```
would mean that visiting `http://1.2.3.4:19181/will/not/guess/me` from a web browser will allow ingress to your EC2 instance on port 24 from the IP making the HTTP request.
Similarly, visiting `http://1.2.3.4:19181/will/not/guess/me/bye` will revoke the ingress rule — or if that URL is not invoked from the same IP within 30 minutes (because `-expiration` was not specified, the default value,) the server will auto-expire the session and revoke the ingress rule.

If you wish to have systemctl manage running this, feel free to modify the .service file included at the root level of this repository.
