# AuthThingie

[![Build Status](https://travis-ci.org/LtHummus/AuthThingie.svg?branch=master)](https://travis-ci.org/LtHummus/AuthThingie)

AuthThingie (names are hard, ok?) is a simple web server that can be used with Traefik's Forward Authentication setting to provide SSO-ish access to a website. I wrote this because I have a home server running a bunch of docker-containers and frontend by Traefik. Each service originally used Traefik's Basic-Auth plugin, which worked fine, except you had to authenticate yourself to each site separately. I found other applications for doing a SSO-approach, but I didn't want to set up something heavy like [Keycloak](https://www.keycloak.org/) or [Authelia](https://www.authelia.com/). And I didn't want to have to rely on a third party to authenticate (so [traefik-forward-auth](https://github.com/thomseddon/traefik-forward-auth) was also out). I also thought it might be a fun thing to write.

**AuthThingie is still a work in progress! Be careful!**

## Configuration

Everything is handled in a `auththingie_config.conf` file. Here's an example:

```hocon
auththingie {
  timeout: 24h
  domain: example.com
  timeZone: America/Los_Angeles
  secretKey: "SAMPLE_SECRET_KEY" // this should be a strong and secure key! It can also be specified in the AUTHTHINGIE_SECRET_KEY envvar
  rules: [
    {
      name: "/css* on test.example.com"
      pathPattern: "/css*"
      hostPattern: "test.example.com"
      public: true
      permittedRoles: []
    },
    {
      name: "/js* on test.example.com"
      pathPattern: "/js*"
      hostPattern: "test.example.com"
      public: true
      permittedRoles: []
    },
    {
      name: "/animals* on test.example.com"
      pathPattern: "/animals*"
      hostPattern: "test.example.com"
      public: false
      permittedRoles: ["animal_role"]
      timeout: 2d
    },
    {
      name: "/colors* on test.example.com"
      pathPattern: "/colors*"
      hostPattern: "test.example.com"
      public: false,
      permittedRoles: ["animal_role"]
    },
    {
      name: "test.example.com root"
      hostPattern: "test.example.com"
      pathPattern: "/"
      public: true
      permittedRoles: []
    }
  ]

  users: [
    {
      htpasswdLine: "ben:$2y$05$WvtSdzLmwYqZqUe/EdLt1uG250dUmHAdQ4nKEDP.J5KRM2u3JbTCS"
      admin: true
      roles: []
    },
    {
      htpasswdLine: "dog:$2y$05$/WME1Gi5RRG/or8To0BjQewJ6lg0z/IyaRjLyhW8yx0ygVwMoJjGO"
      admin: false
      totpSecret: "T2LMGZPFG4ANKCXKNPGETW7MOTVGPCLH"
      roles: ["animal_role", "foo_role"]
    }
  ]

  authSiteUrl: "http://auth.example.com"
  
  # this section is optional and only needed for Duo Security integration (see below)
  duo: {
    integrationKey: XXXXXXXXXXXXXXXXXX
    secretKey: yyyyyyyyyyyyyyyyyyyyyyyyyy
    apiHostname: api-zzzzzzzz.duosecurity.com
  }

}

``` 

Everything should be more or less self-explanatory at this point. Essentially, you have your users and your rules. Every user needs an `htpasswdLine` (essentially the output generated from `htpasswd -nB <username>`), a flag indicating if they are an admin, and a list of roles that the user has.

Each rule has a `name`, a list of `permittedRoles` (which can be empty for admin only), a `public` flag (public means everyone is allowed, logged in or not), and then at least one of `hostPattern`, `pathPattern`, or `protocolPattern` to match against. Any of those three not specified means "ANY". Everything is specified using simple wildcards: `?` matches a single character, and `*` matches many characters (on the todo list is a rule tester). Any path that matches no rules is implicitly "admin-only." Admin users implicitly have access to everything.

`auththingie.authSiteUrl` should be the public URL of the authentication site so AuthThingie can redirect properly. `auththingie.domain` should be the root domain of your server. For example, if all of your sites are `*.example.com` (like `auth.example.com`, `foo.example.com`, etc, this should be set to `example.com`)

TIP: if you want your sessions to auto expire, you can set the `auththingie.timeout` config value to the session duration (for example `12h`).

When a user needs to log in, the user will automatically be redirected to a login page. Once logged in, the user will be redirected back to where they were going (if they have permission). Additionally, credentials can be passed in using basic-auth (via the `Authorization` header). This is useful if you have an app that interacts with a service behind your authentication but can't handle the redirects properly. If you have something that conflicts and uses the `Authorization` header for its own purposes, you can override the header that AuthThingie looks at by setting the `auththingie.authHeader` config value.

### Config Values

#### Main configuration
Here's a table of all the configuration values

| Key                       | Required | Value                                                                                                                                                                       |
|---------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `auththingie.rules`       | Yes      | An array of the rules to apply for paths                                                                                                                                    |
| `auththingie.users`       | Yes      | An array of users for the system                                                                                                                                            |
| `auththingie.authSiteUrl` | Yes      | The full URL of the authentication site (for redirection purposes)                                                                                                          |
| `auththingie.secretKey`   | Yes      | The key to sign session information with. This key should be kept secret. It is also set from the `AUTHTHINGIE_SECRET_KEY` environment variable. (Env var takes precedence) |
| `auththingie.domain`      | Yes      | The root domain for your system. For example, if your sites are `auth.example.com`, `files.example.com`, etc., this should be set to `example.com`                          |
| `auththingie.siteName`    | No       | The name of the site. Show on login pages.                                                                                                                                  |
| `auththingie.timeout`     | No       | Determines how long logging in is good for. If not set, cookie is cleared on browser exit. Takes durations like `1h`, `2d`, etc.                                            |
| `auththingie.authHeader`  | No       | The name of the header to use for basic auth. Defaults to `Authorization` if not set                                                                                        |
| `auththingie.duo`         | No       | The configuration for Duo security if desired (see section below)                                                                                                           |

#### Rule Configuration
Rules consist of the following values

| Key              | Required | Value                                                                                                                                                 |
|------------------|----------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`           | Yes      | A name for the rule                                                                                                                                   |
| `pathPattern`    | No       | The path the request should match. If not given, matches any.                                                                                         |
| `hostPattern`    | No       | The host the request should match. If not given, matches any.                                                                                         |
| `public`         | No       | If the path should be considered "public" (i.e. accessible without logging in). Defaults to `false`                                                   |
| `permittedRoles` | No       | A list of roles that are allowed to access this path. If empty or not given, defaults to `admin` only.                                                |
| `timeout`        | No       | Set a custom timeout for this rule (i.e. a user must have authed within the timeout to be allowed access). If not set, uses the global timeout above. |

#### User Configuration
Users consist of the following values

| Key          | Required | Value                                                                                                              |
|--------------|----------|--------------------------------------------------------------------------------------------------------------------|
| `passwdLine` | Yes      | The output of `htpasswd -nB <username>`. This generally looks like `<username>:<hashed+salted password>`           |
| `admin`      | No       | If this user is an `admin`. Defaults to `false` if not given                                                       |
| `duoEnabled` | No       | If the user uses Duo second factor auth (see section below). Defaults to `false`                                   |
| `totpSecret` | No       | If the user has a TOTP token, this is the secret. This can be generated with the built in `generate_totp` command. |
| `roles`      | No       | Roles that this user has. These should match up with ones in the path rules.                                       |

#### Duo Security Configuration

If Duo security is used, the following values must be set.

| Key              | Required | Value                                                                 |
|------------------|----------|-----------------------------------------------------------------------|
| `integrationKey` | Yes      | Duo integration key.                                                  |
| `secretKey`      | Yes      | Duo secret key.                                                       |
| `apiHostname`    | Yes      | Duo hostname. Should be in the format `api-xxxxxxxx.duosecurity.com`  |


#### Final note

Note that the parser is smart when it comes to `.`. The following configs are equivalent:

```hocon
auththingie.domain: example.com
auththingie.siteName: Example Site
```

and

```hocon
auththingie {
  domain: example.com
  siteName: Example Site
}
```

### Upgrading from 0.0.x series

In AuthThingie 0.1.0, I changed the config file format a bit. For now, things are backwards compatible (though you'll get warnings in the logs and in the admin control panel if you are using the old format...that might be why you're here). Anyway, I removed a bunch of stuff you don't need to worry about anymore (basically anything under the `play` key explicitly except for the secret key). Everything else moved under the `auththingie` key (and `auth_site_url` was changed to have a more consistent name). The above example file should be a reasonable guide in how to configure things (the formats of users and rules haven't changed, just the config key).

## TOTP

AuthThingie supports Time-Based One-Time Passwords. They do require some special setup. AuthThingie includes a script to help generate secrets and set up your app of choice. Once you have AuthThingie up and running in a docker container, you can generate everything with `docker exec -it <container name> generate-totp <username>` (if you are using Docker Compose, you can do `docker-compose exec auth generate-totp <username>`). This will generate a random secret for you and give you instructions on what to do next. The app will also display a QR code in your terminal for scanning in to your authentication application as well as a field to add to your config file. Scan the code, update the config file, then restart the container and you should be good to go.

## Duo Security

AuthThingie supports second factor authentication via [DuoSecurity](https://duo.com/) push notifications. Sign up for a free account and install the app on your smart phone. In the Duo Admin control panel, add a new Application with the type "Partner Auth API".  You should be given an integration key, a secret key, and an API hostname. Create a user in the Admin panel as well. The username of your Duo user MUST MATCH the AuthThingie username. In the AuthThingie config file, add a section called `duo` inside the `auththingie` section. Additionally, add `duoEnabled: true` to each user you want to enable. The user in Duo must support push notifications, as that is all that AuthThingie supports.

## Deployment

This is meant to be used with traefik's forward authentication plugin. If you just want to get up and going and see how it works, I have included a `docker-compose.yaml` file that has an example service running at `test.example.com` and the authentication running at `auth.example.com`. You will need to add entries for both to your HOSTS file to get the domains to work. Add something like

```
127.0.0.1 test.example.com
127.0.0.1 auth.example.com
```

to your computer's hosts file, then run `./build.sh` to boot everything (you will need `sbt` installed to compile the service). Run `docker-compose down` to tear everything down when you are done. In this example config, the root of `test.example.com` is allowed by everyone. Going anywhere else should redirect you to the login page. The username/password for the admin user in the sample config is username `ben` and password is `abc`. There is also the account `dog` with the password `woof` that can only access the animal section of the sample website. (See https://github.com/LtHummus/SampleSite for more info on the sample site).

### Deployment for real

Create a `auththingie_config.conf` file somewhere on your file system. Create the docker container from the image and mount that config file in the container. Set the environment variable `AUTHTHINGIE_CONFIG_FILE_PATH` to point to where the config file lives in the container. Set your Traefik config to point forward authentication to `/auth` on the server See the included `docker-compose.yaml` file for a complete example. Note the `traefik.frontend.auth.forward.address: "http://auth:9000/auth"` label on the sample website.

## You are likely to be eaten by a grue
**Remember! This is a work in progress!**
 
This is like 95% experimental right now. There are definitely some things that are rough around the edges. You can run this if you want (let me know how it goes!), but this is definitely an "at your own risk" situation. I'm reasonably sure that this works, but anyone can build a lock that they themselves can't break.

***Help, I'm in a redirect loop!***

Add the auth server as a rule with public access.

## TODO List

In no particular order...

* Perhaps read config from a database?
* WebAuth?

## Credits

* [Tara Favazza](https://github.com/tfavazza) - Bootstrap Advice
* [Josh Harrison](https://twitter.com/joshharrison) - Lots of beta testing and feedback

### AuthThingie Uses Some Open Source Software

|         |        |       |
|---------|--------|-------|
| **Play Framework** | https://www.playframework.com/ | Copyright 2009 - 2019 Lightbend Inc.
| **configs** | https://github.com/kxbmap/configs | Copyright 2013 - 2016 Tsukasa Kitachi
| **Scalatest Play** | https://github.com/playframework/scalatestplus-play | Copyright 2001 - 2016 Artima, Inc 
| **Mockito Scala** | https://github.com/mockito/mockito-scala | Copyright 2007 Mockito Contributors
| **Apache Commons** | http://commons.apache.org/ | Copyright 2002 - 2019 The Apache Software Foundation 
| **Bcrypt Java** | https://github.com/patrickfav/bcrypt | Copyright 2018 Patrick Farve-Bulle
| **zxing** | https://github.com/zxing/zxing | Copyright 2007 - 2019 
| **scopt** | https://github.com/scopt/scopt | Copyright scopt contributors
| **Play Bootstrap** | https://adrianhurt.github.io/play-bootstrap/ | Copyright 2014 - 2019 Adrian Hurtado
| **Boostrap** | https://getbootstrap.com/ | Copyright 2011 - 2019 Twitter; Copyright 2011 - 2019 The Bootstrap Authors
| **Open Iconic** | https://useiconic.com/open | Copyright 2014 Waybury



