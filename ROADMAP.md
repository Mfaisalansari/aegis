Sprint 1

✓ Browser

✓ Navigation

✓ Click

✓ Type

--------------------------------

Sprint 2

✓ Mission

✓ Observer

--------------------------------

Sprint 3

✓ Planner

✓ Decision Engine

--------------------------------

Sprint 4

✓ Goal Reasoner

--------------------------------

Sprint 5

✓ Candidate Generator

✓ Action Scorer

--------------------------------

Sprint 6

✓ Candidate Filter

✓ Goal Evaluator (Mission Outcome Detection)

✓ Explainability Dashboard (in-app report, not a web UI)

--------------------------------

Sprint 7

✓ Generic Exploration

--------------------------------

Sprint 8

✓ Exploration Strategies (Greedy, Random, Risk-Based, Breadth-First, Depth-First, Form-First, Navigation-First — all but Greedy/Random/Risk-Based are element-tag proxies, not real graph search; that needs World Model, Sprint 10)

--------------------------------

Sprint 9

✓ Memory (in-run only — visited pages/states; not persisted across runs)

--------------------------------

Sprint 10

✓ World Model (navigation graph only — no dialog/form modelling)

--------------------------------

Sprint 11

□ LLM Integration

--------------------------------

Sprint 12

□ Learning Engine

--------------------------------

Sprint 13

□ Autonomous Exploratory Testing


                      Mission
                         │
                         ▼
                  Mission Engine
                         │
      ┌──────────────────┼──────────────────┐
      ▼                  ▼                  ▼
Observer            Planner            Executor
│                  │                  │
▼                  ▼                  ▼
Observation     Decision Engine      Action Handlers
│
▼
Goal Evaluator
│
▼
Goal Reasoner
│
▼
Candidate Generator
│
▼
Candidate Filter
│
▼
Action Scorer
│
▼
Best Action
│
▼
Playwright