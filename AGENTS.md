# Agent Guidelines & Principles (Karpathy Guidelines)

Behavioral guidelines to reduce common LLM coding mistakes, derived from Andrej Karpathy's observations on LLM coding pitfalls. These guidelines apply to all coding, debugging, and refactoring tasks in this repository.

---

## 1. Think Before Coding
**Don't assume. Don't hide confusion. Surface tradeoffs.**
- State assumptions explicitly before implementing. If uncertain or ambiguous, ask the user.
- If multiple interpretations exist, present them clearly—do not pick silently.
- If a simpler approach exists, propose it. Push back against unnecessary complexity.
- If something is unclear or conflicting, stop, identify what is confusing, and ask.

---

## 2. Simplicity First
**Minimum code that solves the problem. Nothing speculative.**
- Implement only what was requested—no unrequested features or preemptive abstractions.
- Avoid over-engineering single-use code into generic frameworks.
- No defensive error handling or configurability for impossible scenarios.
- Keep diffs small and direct: if 20 lines can solve the issue cleanly, do not write 100 lines.
- Ask: *"Would a senior engineer say this is overcomplicated?"* If yes, simplify.

---

## 3. Surgical Changes
**Touch only what you must. Clean up only your own mess.**
- Never "improve", reformat, or alter adjacent code, comments, or docstrings outside the task scope.
- Preserve existing style, architecture, and comments.
- Do not refactor functional parts that are not broken.
- When changes orphan imports or variables created by this task, clean them up. Leave pre-existing code alone unless explicitly instructed.
- Every changed line must directly trace to the user's request.

---

## 4. Goal-Driven Execution
**Define success criteria. Loop until verified.**
- Transform requests into verifiable criteria before declaring done.
- For multi-step tasks, define a brief step-by-step plan:
  ```
  1. [Step] -> verify: [check]
  2. [Step] -> verify: [check]
  3. [Step] -> verify: [check]
  ```
- Perform self-testing and verification before handing off to the user.
- In this project (Wall-E Notch), verify logic with local simulation scripts and confirm remote builds succeed via GitHub Actions.

---

## 5. Knowledge Graph & Architecture Mapping (Graphify)
**Always maintain and query the codebase graph.**
- Always consult `graphify-out/` (`graphify query`, `graph.json`, `GRAPH_REPORT.md`) to trace call flows, data dependencies, and god nodes before making architectural changes.
- After every significant feature addition or refactoring, automatically run `graphify update .` to keep `graph.json`, `GRAPH_REPORT.md`, and the visual map `graph.html` 100% synchronized with the latest codebase.
