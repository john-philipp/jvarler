> Note this readme is still under construction.

# JVarler (JV)

A Java based multi-file configuration engine. Allows simple consistent configuration across any number of files in any format. Leverages Jinja2 for templating + some extended functionality wherever Jinja2 falls short in my experience.

## What it can do

Think about a stack with many different components, each relying on their own configuration file in whichever format. You require consistent configuration throughout the stack, but especially during development this configuration may require transient changes. IPs or domains, you might be spinning up a varying number of instances, for example.

In the classic case you require definition of static configs before the fact. Some of which may be able to leverage env variables. Any developer working with such a system relies on knowing where the configs they need to change live or copy and paste and alter for new nodes.

The JVarler can provide you with a simple way to predefine joint config based on which all other config in any (reasonable) location is generated with simple override functionality not requiring to edit files each time (a simple command line override at invocation is enough).

At the same time the source config also is templatable relative to itself (something of a page-wise meta-template). Similarly, the destinations definition (the file where all source templates and destination locations of resolved configuration are defined) allows templating using Jinja2.

As a consequence you won't have to worry about consistent config anymore, you change config in one place and any developer can go to a single file (`destinations.yaml` by convention) to see all locations config is written to.

# Getting started

## Prerequisites

- Java 11: Later versions should work, too.
- Maven 3.6.3: Later versions should work, too.
- Some bash based command line.

If it's all the same to you, the following should do:
```
curl -s "https://get.sdkman.io" | bash 
sdk install java 11.0.21-zulu
sdk install maven 3.6.3
```

There are no real limitations on versions. Nothing explicit, anyway. Clearly, steering too far off the beaten track will lead to possibly unintentional adventure.

## Using Makefile

### clean/clean-samples/clean-all
Remove `target/` and/or any samples output (located in `samples/**/generated`), respectively.

### build
Build using local maven/java versions, generates jar with deps in `target/jvarler.jar`:
```
make build
```

### run-sample

Samples are located in `samples/`. They are denoted by index with two leading zeroes. Sample `001` may be run as:
```
make run-sample sample=001
```

# Quick glossary and explanations

## Source config `vars.yaml`

The source config. A type of centralised config file. All values propagate from here. Is itself a template based on itself or feeding configurations from previous JV invocations. Arbitrary map.

## Destinations `destinations.yaml`

Determines configuration destination locations. Defines where config is written to. A list of destination entries:
```
- shell:        # Run using shell. Kind of dangerous. Only at beginning of document. 
                # Will be executed single threaded and in order specified.
  store:          # [Shell only] Store output in this path.
  stripRight:     # [Shell only] Int, strip result from right.
  stripLeft:      # [Shell only] Int, strip result from left.
  
  source:       # Load template from this path
  destination:  # Write resolved config here.
  destinations: # A list of destinations to write to.
  variables:    # Map of variables additionally available in resolution context.
```

## Exports `exports.json`

The final resolved version of the config supplied. Helps spotting errors but also useful as a query source in scripts using `jq`, for example.

# Features

Some description of hopefully most of the things the JV can do.

## Pagewise meta-templating in source config

A source config is the central point of your configuration based on which all downstream configurations will be generated using their own individual templates. The source config has two types of syntax, Jinja2 and JV, both described below.

That is, the source config is itself a template based on some supplied JSON (can be the export result of a previous JV invocation). Furthermore, the source config makes use of pages to resolve values relative to itself. That is `page[n+1]` may reference any value available as per `page[n]`.

See below for an example. We're assuming that no outside JSON is fed into our invocation. As a consequence all values on `page[0]` must be static. However, using the JV syntax we can still reference values that either are available now or will be based on a later page.
```
# page[0]
a: 1
b:
    c:
        # Here `/` denotes root of document.
        d: ${/a} 
        e: ${/z}

---
# page[1]
z: 2
```
resolves to
```
a: 1
b:
    c:
        d: 1
        e: 2
```

When loading a config all the first page is read, loaded as YAML with no Jinja2 being applied (unavailable on page 0). JV resolutions are applied. Any variable unavailable in current context is left as variable reference (allows for env variable inclusion and delayed reference). Page 0 forms our first *running config* to which we'll keep adding on a per page basis.

Loading any page `n > 1` we'll render it using our running config, then update the running config and resolving any JV values afterwards. It's important to understand the order of syntax application.

## Syntax

There are two separate types of syntax available. Jinja2 and a JV syntax. In a source config template both types are available, in a destinations file and a configuration template only Jinja2 is available to keep things easy.

### Jinja2

Roughly speaking anything Jinja2 is available. There are some limitations through the Jinjava module. A notable example (last time I checked) is in-line `if` clauses within a `for`:
```
{% for x in y if x %} # Won't work.
{% for x in y %} # Will work.
    {% if x %}
```
So long Jinjava supports it, it's supported here.

### Relative variables

I added the JV syntax to extend functionality provided by Jinja2. I really wanted something to allow me to specify relative variables based on current node within config. For example,

```
a: 1
b:
    map:
        value: ${../a} == ${/a}
    list:
     - ${../../a} == ${/a}
```
resolves to
```
a: 1
b:
    map:
        value: 1 == 1
    list:
     - 1 == 1
```
Here we move a context up using `../` within the variable reference. Using `/` denotes a node at the root of the document.

### Delayed variable resolution
Variables specified using the jvarler syntax don't necessarily need to be available in the context they are first specified. See e.g. sample `002`. Note the page delimiter `---`.
```
a: ${b}
---
b: 1
```
resolves to
```
a: 1
b: 1
```
Notably, Jinja2 has trouble with this.


### Nested variable resolution
Variable references may themselves be the result of variable references. For example
```
a: 1
b1: 2
c2: abc
d: ${/c${/b${/a}}}
e:
  f: ${/c${../b${../a}}}
```
resolves to
```
...
d: abc
e:
    f: abc
```
See sample `003`.

### In-line invocation overrides

In order to change things easily, we need a way to quickly apply and propagate overrides to all of our config on-the-fly. You can provide overrides using the `-o` flag (e.g. `make run-sample sample=001 args="-o a=2"`). As a further example:
```
a: 1
b:
  c: a

---
d: 2
e:
  - a
  - b
  - c
```
with `args="-o a=2 b.c=b e=[d,e,f]` resolves to
```
a: 2
b:
    c: b
d: 2
e:
 - d
 - e
 - f
```
Note the simplified override for arrays. In bash strings in string can become cumbersome, so I try to match types on existing arrays. This is non-configurable at the moment. Consistent usage of types in an array appears like a reasonable assumption that, if violated, highlights more of a data type consistency than a pattern issue.

Further, note that any override must be available on page 0. Otherwise, you'll get a non-meaningful override error.

### Jinja2 in source config

As per page 1 (that is, the second page - or the effective second page in multi config setups) you can make use of Jinja2 within the source config.
```
a: 1
---
b:
{% for i in range(a) %}
 - {{i}}
{% endfor %}
```
resolves to
```
a: 1
b:
 - 0
```
See sample `005`.

Overriding `a=3` (`make run-sample sample=005 args="-o a=3"`) we get
```
a: 1
b:
 - 0
 - 1
 - 2
```

## Destinations

The destinations file is where configuration templates and output locations are defined. It's an array of structs. For the most part it'll define `source` and `destination` as paths. However, the entire destinations file is Jinja2 templatable. Specifically, assuming a config of the form:
```
n: 3
```
Then our destinations file can use this to template three possible outputs to our source `template.yml`:
```
{% for i in range(n) %}
- source:       template.yml
  destination:  generated/resolved-{{i}}.yml
  # Specifies local variables during template resolution.
  variables:
    i: {{i}}
{% endfor %}
```
Resolving to:
```
- source:       template.yml
  destination:  generated/resolved-0.yml
  variables:
    i: 0
- source:       template.yml
  destination:  generated/resolved-1.yml
  variables:
    i: 1
- source:       template.yml
  destination:  generated/resolved-2.yml
  variables:
    i: 2
```
See sample `006`. We can combine this with overrides in the usual way.

### Using the shell and storing shell output

For convenience there's a shell available in the destinations file. All shell commands must be at the top of the file and will be executed prior to any configuration in order as specified. Config production occurs in parallel. Honestly, the shell is kind of dangerous and I should likely provide the possibility to disable it using a build profile. This, as it stands, however, is not the case.

Consider the following setup:
```
> vars.yaml
a: a

> template.yml
b: {{b}}

> destinations.yaml
- shell: echo strip{{a}}strip
  store:      b
  stripRight: 5 # Strip `strip$`.
  stripLeft:  5 # Strip `^strip`.

- source: template.yml
  destination: generated/resolved.yml
```

This resolves to:
```
> generated/resolved.yml
b: a
```

The input here didn't necessarily need to come from `vars.yaml` any shell output would've done.

## Providing input JSON
Input JSON can be provided. Since any invocation produces JSON via export, invocations can be chained in this way. See sample `008` (`make run-sample sample=008 args="-j samples/008/input.json"`):
```
> input.json
{"a": 1}

> vars.yaml
{}

> template.yml
a: {{a}}

> destinations.yaml
- source: template.yml
  destination: generated/resolved.yml
```
resolves to
```
> generated/resolved.yml
a: 1
```

# Extensions

- I'm thinking of adding a docker based build env and a docker based runner to not depend on possibly locally installed versions.