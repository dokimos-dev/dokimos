# Conversation Golden Generation

This is the example code for the [Generate Conversation Test Data](https://dokimos.dev/tutorials/generate-conversation-test-data) tutorial.

## Generate the Suite

```bash
export OPENAI_API_KEY='your-api-key'
mvn exec:java -pl dokimos-examples \
  -Dexec.mainClass="dev.dokimos.examples.conversation.goldens.GenerateSupportGoldens"
```

This runs three scenario seeds against `SupportDesk` and writes the conversations to
`dokimos-examples/src/test/resources/datasets/support-goldens.json`. Pass a path as the first
argument to write somewhere else. Regenerating overwrites the file, so review the diff before you
commit it.

## Replay the Suite

```bash
RUN_EVAL_TESTS=true OPENAI_API_KEY='your-api-key' mvn test -pl dokimos-examples -Dtest=SupportGoldenReplayTest
```

The test pulls the recorded `USER:` turns out of each golden, replays them against the current
`SupportDesk`, and grades the conversation that comes back against the golden's `expectedOutcome`.
A prompt or model change that stops the desk reaching the outcome fails the case it belongs to.

Note: The test is skipped by default. Set `RUN_EVAL_TESTS=true` to run it, and export
`OPENAI_API_KEY`, or it is skipped too.

## Structure

```
goldens/
  SupportDesk.java             # The application under test, backed by an OpenAI chat model
  GenerateSupportGoldens.java  # Seeds, generation, and the write to src/test/resources

test/
  SupportGoldenReplayTest.java             # Replays the user turns against SupportDesk
  resources/datasets/support-goldens.json  # The committed suite
```

See the [full tutorial](https://dokimos.dev/tutorials/generate-conversation-test-data) for the walkthrough, and the [Multi-Turn Conversations](https://dokimos.dev/evaluation/multi-turn-conversations) guide for the reference material on seeds and goldens.
