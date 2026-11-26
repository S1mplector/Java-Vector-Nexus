# Tutorial

Choose **Start tutorial** to meet Ginger. Her introduction leads straight to the pannable topic tree with all 52 lessons. Drag to pan, scroll or pinch to zoom, click a category to focus on its lessons, use Find a lesson to search by title or category, and use Fit map to reset the view. Full screen or F11 toggles the game window between full screen and its previous size. Lessons can be opened in any order. **Back to topics** records completion and returns to the same category. Replaying a lesson counts once. **Progress and motion** shows your total and the motion options.

Lessons include worked examples and editing exercises. Many have a review question with feedback. The workshop lessons let you change a variable, compare the result, or edit a project file and repeat the example.

The code panel shows numbered lines from the project file named in its header. Save your edit and reopen the lesson to see the updated source. The panel reads the lines between the region comments, so you can add or remove lines without renumbering anything.

To edit an example, open its numbered file in `scripts/tutorial/`, change one value, save, and replay. Three starting points:

- **Writing and branching → Dialogue** demonstrates speaker IDs, emphasis and inline effects. Move the bold tags and compare the rendered line.
- **Text and interfaces → Reactive UI** opens the same panel at 25% and 75%. Check the bar and the Continue button in each case.
- **Game systems → Saving and rollback** lets you save before a choice, set a value, then reload and test the other branch.

The editable map is in `config/ui/tutorial-topics.properties`. Its choice targets are authored in `scripts/story/tutorial_hub.vns`. Each card has an optional PNG icon and a completion flag. The same VNS category choices work on players that use the standard choice renderer. Sprite and backdrop declarations are in `scripts/definitions/characters.vns`. Their image files are in `assets/tutorial/`.
