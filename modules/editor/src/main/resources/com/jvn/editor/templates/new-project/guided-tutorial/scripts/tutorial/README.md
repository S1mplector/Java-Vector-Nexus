# Tutorial

Choose **Start tutorial**, then select a topic and a lesson. Lessons can be opened in any order. **Back to topics** records completion and returns to the same category. Replaying a lesson counts once. **Progress and motion** shows your total and the motion options.

Each lesson includes a worked example and a practice exercise. The exercise asks you to predict or diagnose a specific result. A wrong answer gives you a hint and lets you try again.

The code panel shows numbered lines from the project file named in its header. Save your edit and reopen the lesson to see the updated source. The panel reads the lines between the region comments, so you can add or remove lines without renumbering anything.

To edit an example, open its numbered file in `scripts/tutorial/`, change one value, save, and replay. Three starting points:

- **Writing and branching → Dialogue** demonstrates speaker IDs, emphasis and inline effects. Move the bold tags and compare the rendered line.
- **Text and interfaces → Reactive UI** opens the same panel at 25% and 75%. Check the bar and the Continue button in each case.
- **Game systems → Saving and rollback** lets you save before a choice, set a value, then reload and test the other branch.

The topic menu is in `scripts/story/tutorial_hub.vns`. Sprite and backdrop declarations are in `scripts/definitions/characters.vns`. Their image files are in `assets/tutorial/`.
