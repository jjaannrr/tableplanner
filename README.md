# TablePlanner

## Why?

TODO

## Approach

This was used as a Kotlin playground. I am a Java developer who haven't managed to get past 2nd chapter in Kotlin in Action yet.
Most of the time was spent finding how to achieve certain basic coding in Kotlin, whilst still trying to follow recommended style and approach in Kotlin.
This affected some design decisions, which I haven't tackled yet.

### Basic Rules

Based on our virtual lunch sessions the logic follows these rules:
* Each table has a host assigned who doesn't change tables => This determines table name
* At the end of a session, each guest will move to another table they haven't been at previously

### Evaluating and assigning guests to tables

#### Initial approach
The initial attempt was trying to proactively seat guests based on guest's rating (highly penalising tables where there was another guest whom this guest already met).
This lead to being stuck with a certain solution and not necessary a good one. Still struggling with trying to use "good" Kotlin code it took a bit to realise that this was a dead end.

#### Second approach
Next came a bit of randomness into the logic. The quality of results improved, but it was still a bit hit-and-miss.
The initial logic found some nice solutions, but struggled in certain scenarios where it picked on 1 guest who met only a few other guests and always ended up following someone to another table, where most of the guests have always went through meeting someone new.
With the low number of tables and guests it must be far simpler to just randomise and run sufficient number of times.  

#### Third approach

Third approach built on top of the data model of the previous ones but added few more parameters to score the plan.
1. Follow-up score - discourage plans where guests follow each other up to the table in the next session. 
_With some starting conditions, follow-ups are unavoidable. In those scenarios, make sure all users "share" the burden,
 and it isn't a specific guests who meet at several tables in the consecutive sessions._
1. Diversity score - prefer plans where each guest meets the same amount of other guests where possible.
1. Average guest score - guest score is calculated as BASE_SCORE ^ no_of_meetings_with_other_guest to make sure scenarios,
 where guests meet other guests multiple times, these multiple meetings are shared across all the guests.
1. Table score - prefer plans where each table hosts see the same amount of guests over all the sessions.
_(Not possible with some starting conditions. Where possible use only plans with even distribution of guests
 across tables over the sessions.)_

**Finding next table for a guest**

Few methods to allocate next table are available (though these are currently hardcoded based on initial conditions):
1. `LookAheadTableAllocator` - original logic from 'Initial approach'. It is fairly good at finding solutions when an "ideal" 
distribution _(combination where every guest meets a different person in each session)_ is available. 
Due to randomness it still requires few runs (but usually tens of runs)).
1. `RandomTableAllocator` - pure random allocation. This is not very good given additional rules applied in 'Third (current) approach'.
1. `LeastGuestsRandomTableAllocator` - allocate next table based on number of guests already sitting there. This tends to generate plans
suitable when aiming to achieve even guest distribution across tables.

## How to build & run

The code contains 2 versions. 

_Kotlin and Java (Java was created as decompiled version of Kotlin and not yet fully pruned). 
Most lambda logic had to be coded again as the decompiled version was unreadable._

_I kept both to allow comparison of the code between the two languages._

### Build
Kotlin version builds by default. Java version needs to be activated through a profile. _Both versions will still end up the final assembly. 
(I will attempt to separate later.)_

**Kotlin**
```shell script
mvn clean package
```
**Java**
```shell script
mvn clean package -P java
```

### Run
* Unless names are provided, tables and guests use 1-based indexes:
   ```shell script
   java -jar tableplan.jar -g <number of guests (excluding hosts)> -t <number of tables> -s <number of sessions>
   ```
* Names can be provided in a text file (one name per line) and output printed into CSV file (with stats still printed to the console).
  1. Number of hosts is determined by number of tables (first names from the top will be used as hosts).
  1. Remaining names will be used as guests _(`-g` parameter is ignored)_.
  1. By default, it assumes 4 tables and 4 sessions. 
  ```shell script
  java -jar tableplan.jar -i <input file name> -o <output file name>
  ```

#### Performance

If an "ideal" solution is found, the calculation is terminated straight away. Otherwise, 10,000 plans (controlled by `-it` option)
 is generated and assessed and "best" result is returned.
 The calculation uses 8 threads by default (can be controlled by `-th` option).

Default scenario (16 guests and 4 hosts in 4 sessions) takes ~0.75s with i7-8550U CPU.

## Kotlin & Java

TODO