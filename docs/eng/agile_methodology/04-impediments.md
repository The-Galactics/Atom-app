# Impediment Management [Atom]:

To maintain an orderly workflow, this document was created to record the stoppers and blockers encountered during the development of the AI assistant named Atom.

## Documentation and Tracking:

To maintain an organized record of stoppers and blockers for each sprint, a specific order is suggested for managing and registering them. They are identified by an ID (e.g., Imp-01), a date for exact tracking, the type of impediment (either a stopper or blocker), a brief description, how it impacts development, the person responsible, and the current status of the task, with the aim of resolving it at the root and being aware of points for improvement. This allows for feedback during our retrospective at the end of the sprint.

### Impediment Status:
Status levels allow us to track exactly whether a problem is being solved or not; these are divided into three categories and colors:

- **🔴 Pending:** When a stopper or blocker is newly detected, and the corresponding person responsible for the task is being sought.
- **🟡 In Progress:** When the stopper or blocker has been assigned to the responsible person and is being worked on.
- **🟢 Completed:** When the impediment has been resolved at the root.

### Impediment Impact:
The impact of the impediment is determined by the priority and difficulty of the process for the App, categorized from High to Low, followed by the functional requirement affected by the impediment.

- **High:** When it is a very important and priority functionality for the App's development and would eventually generate performance loss in the workflow.
- **Medium:** When it is something important but would not greatly affect development and has a more manageable solution.
- **Low:** When the impediment does not directly affect the App's functioning but should still be taken into account.

### Impediment Detail and Solutions:

Each impediment must follow a specific structure to identify the root cause, the actions performed to solve the impediment, and finally the resolution, where it is determined if the impediment is finished or needs validation from the PO or Scrum Master to proceed.

### Workflow and Resolution Example:

|   ID   |    Date    |  Type   |                           Description                           |       Impact        |       Responsible       |   Status   |
|:------:|:----------:|:-------:|:---------------------------------------------------------------:|:-------------------:|:-----------------------:|:----------:|
| Imp-01 | 05-09-2026 | Stopper | Compatibility error between the floating bubble and Android 14. | High (Blocks RF-04) | Scrum Master / Dev Team | 🔴 Pending |

---
#### [IMP-01] Android 14 Compatibility
- **Root Cause:** "Display over other apps" permissions changed in the latest API.
- **Action:** Investigate new Google security requirements.
- **Resolution:** *Pending validation.*

---

# Impediments - Sprint 1:

|   ID   |    Date    |  Type   |                         Description                         |         Impact          | Responsible  |    Status    |
|:------:|:----------:|:-------:|:-----------------------------------------------------------:|:-----------------------:|:------------:|:------------:|
| Imp-01 | 04-06-2026 | Stopper |         Issue creating branches directly from Jira          | High (Blocks git-flow)  | Scrum Master | 🟢 Completed |
| Imp-02 | 04-06-2026 | Blocker | Missing folder structure due to GitHub's automatic deletion | High (Blocks work-flow) | Scrum Master | 🟢 Completed |

---
#### [Imp-01] Branch creation from Jira

- **Root Cause:** When attempting to create a branch directly from Jira, the repository was not visible as an option for branch creation.
- **Action:** Created the linkage for the organization's repository to facilitate branch creation.
- **Resolution:** *Completed.*

#### [Imp-02] Main structure

- **Root Cause:** When creating the folder structure, due to a lack of awareness regarding GitHub's automatic feature that deletes empty folders, the main project structure was not uploaded.
- **Action:** With the newly acquired knowledge, the folder structure was recreated, but this time adding template files to ensure folder permanence.
- **Resolution:** *Completed.*

---

# Impediments - Sprint 2:

|   ID    |    Date    |  Type   |                   Description                    |           Impacto           | Responsible |    Status    |
|:-------:|:----------:|:-------:|:------------------------------------------------:|:---------------------------:|:-----------:|:------------:|
| Impl-01 | 11-06-2026 | blocker | Lack of attendance at daily meetings by the team | Medium (unawareness of UHs) | Scrum Team  | 🟢 Completed |
| Impl-02 | 12-06-2026 | stopper |      Error when compiling the Java backend       |     High (Blocks UH-11)     | Developers  | 🟢 Completed |  

---

### [Impl-01] Team non-attendance at the daily meeting

- **Root Cause:** Communication issues occurred at the time of the daily meeting.
- **Action:** Remind everyone of the daily meeting time and seek more commitment from all team members.
- **Resolution:** *Completed*.

### [Impl-02] Typo problems

- **Root Cause:** When testing the Java code, it failed to compile.
- **Action:** Create branches to resolve the issues.
- **Resolution:** *Completed*.
