# Github Review Window

A GitHub Webhook Implementation

To run it via Maven and the spring-boot plugin:
```sh
$ mvn spring-boot:run [OPTIONS]
```

To run it on the command line:
```sh
$ java [OPTIONS] -jar gh-review-window-(version)-full.jar
```

Where `OPTIONS` include:

|    option        |            description                                                     |
|------------------|----------------------------------------------------------------------------|
| duration         | **required** This is the default window duration                           |
| duration.`LABEL` | Additional durations for specific labels                                   |
| secret           | The secret that will be used to [compute the HMAC][securing your webhooks] |

For the syntax of period strings, see the [`java.time.Duration` javadoc][javadoc duration].

This is a subset of Spring Boot's autoconfiguration,
see the list of [common application properties][properties] for other supported configuration options.

Example:
```sh
$ mvn spring-boot:run -Dduration=P2D
```

## Oauth

In order to give Review Window the ability to add commit statuses, you need to specify
credentials that it can use to access those.

 - Generate an Oauth token that gives `repo:status` access.
 - Add it to your environment or `~/.github` file as `github_oauth`.


[javadoc duration]: http://docs.oracle.com/javase/8/docs/api/java/time/Duration.html
[properties]: http://docs.spring.io/spring-boot/docs/current/reference/html/common-application-properties.html
[securing your webhooks]: https://developer.github.com/webhooks/securing/
