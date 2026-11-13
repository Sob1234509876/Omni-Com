# Open questions for the owner

A running list so nothing is silently assumed. The agent keeps working while these are open and records the default it chose; answering one changes the default, not the schedule.

The previously answered questions (Q1–Q16) have been removed from this file now that they are resolved; their rulings live on in the code and the docs they produced.

# Still open

## Q5b. `git push` — **open, owner-only**

The working tree is ahead of the remote; every check is green locally but the remote is stale. The agent has no credentials and cannot push. A push from the owner's account publishes both the code and the rebuilt client.
