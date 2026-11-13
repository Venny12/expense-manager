# AI Prompt Log

**Project:** Expense Manager


**Name:** Shaqayeq Salimy  
**AI tool:** ChatGPT  
| Prompt / My Development Activity | How ChatGPT Assisted |
| --- | --- |
| Asked how to divide the testing tasks between both team members. | Suggested a task allocation covering Python, Java, database, and cross-app testing. Included the existing tests in my assigned work. |
| Asked how to create a clear folder structure for test files, documentation, and evidence. | Suggested organizing unit, integration, and SQL tests separately, with reports and screenshots under `docs/evidence/`. |
| Asked what invalid inputs a user might enter during login, expense submission, editing, and manager decisions. | Suggested examples such as empty fields, spaces, text instead of numbers, invalid IDs, incorrect dates, and comments outside the permitted length. |
| Asked which boundary values should be checked for expense amounts, descriptions, dates, and comments. | Suggested values at the limits and just outside them, such as 0, 0.01, 10000.00, 10000.01, and descriptions containing 200 or 201 characters. |
| Asked how to organize the testing work and which assigned area to complete next. | Suggested an order for completing tests, reviewing results, collecting evidence, and updating documentation. |
| Asked how to connect test cases and automated test functions to the RTM. | Explained how to record requirement IDs, test references, execution results, and remaining checks. |
| Requested feedback on documentation wording and consistency. | Suggested simpler wording and clearer distinctions between automated results, manual results, and pending work. |


**Name:** Vennela Preethi  
**Tool:** Claude  
| Prompt | How Claude Assisted |
| --- | --- |
| Shared the project requirements and database task allocation. Asked for feedback on the sample database records and whether they covered the required scenarios. | Suggested sample users, managers, employees, and expense records. Helped validate constraints and identify data issues. |
| Reviewed the expected employee and manager workflows. Asked Claude to check whether my understanding of the menu options matched the requirements. | Reviewed the proposed menu structure and clarified the pending expense workflow. |
| Shared the Jira board and asked for a review of missing requirements, ticket assignments, and workload distribution. | Identified potential gaps in authentication, logging, error handling, and testing tickets. |
| Asked for suggestions on additional Jira tickets to ensure all functional and technical requirements were covered. | Suggested additional tickets grouped by epic and linked to requirement IDs. |
| Requested a review of the existing database implementation and seed data. | Reviewed the schema and initialization approach, and suggested improvements to sample data validation. |
| Asked for the recommended next development task based on the current repository status and merged changes. | Suggested completing shared database utilities before proceeding with individual features. |
| Worked on the SQL validation task and requested examples of validation queries and ways to test different database scenarios. | Provided query examples and suggested corresponding test scenarios. |
| Shared a screenshot of SQL editor warnings and asked whether they represented actual SQL errors. | Explained the SQL dialect warnings and suggested a simpler query implementation. |
| Identified the menu, edit, delete, and history features as the next development priorities. | Provided implementation examples, validation suggestions, and feature-level code assistance. |
| Shared the latest repository changes and asked Claude to ensure new implementations were compatible with the merged database utilities and exception handling. | Reviewed the updated structure and adjusted the suggested implementations to use the existing shared components. |
| Asked to organise the function and why separate menu functions might be preferable to placing all functionality inside one continuous while loop. | Explained maintainability, readability, and Git conflict considerations, with a simpler alternative for comparison. |
| Requested help preparing a pull request description for the completed menu changes. | Suggested a structured PR description covering implementation and changes. |
| Shared the database configuration error and asked for help identifying why the application could not locate the SQLite database. | Explained the EM_DB_PATH environment variable, database initialization, and test account configuration. |
| Shared an unexpected error occurring during expense editing and requested debugging assistance | Identified compatibility issues caused by recent changes in the main branch and helped revise the implementation. |
| Requested the complete edit expense implementation for comparison with my local files. | Provided the relevant implementation and explained the changes. |
| Requested guidance on handling invalid IDs, transaction rollback. | Suggested test cases for deletion and error handling. |
| Started working on expense history. Requested feedback on retrieving the appropriate expense records. | Suggested test scenarios for expense history. |
| Reviewed the Python application progress and asked which Java application components still needed development. | Compared the existing functionality against the requirements and identified remaining Java tasks. |
| Prioritized the Java menu and exception handling. | Suggested Java implementations for Menu, NotFoundException, InvalidStateException, and InputValidator. |
| Shared the updated Jira board and requested a review of the outstanding Java tasks. | Explained that the board required authenticated access and requested screenshots for review. |
| Reported that the Java application exited after displaying the welcome message. Asked for help identifying the menu execution issue. | Helped investigate menu initialization, compilation, and execution flow. |
| Shared the details of exception-handling examples compatible with the existing application. | Provided explanations for the relevant Java files. |
| Shared the NoClassDefFoundError related to org/sqlite/SQLiteConfig and requested help troubleshooting the Java runtime dependency. | Identified the missing SQLite dependency in the runtime classpath and suggested restoring dependencies using Maven. |
| Worked on Java input validation and requested a review of boundary conditions, including expense amounts, IDs, and comments. | Suggested additional validation conditions and boundary test scenarios. |
| Reported that the Java menu required pressing Enter twice. Asked for help identifying the root cause and improving interactive input handling | Investigated the Scanner input-handling issue and suggested using Console for interactive input while retaining the Scanner-based constructor for scripted tests. |
| Asked Claude to review the basic fundamentals of JUnit 5 and Mockito before writing the Java tests. | Explained the core JUnit 5 annotations and assertions, and how Mockito creates mocks, verifies calls, using short examples. |
| Asked about parameterized tests in pytest and asked Claude to review the examples I wrote. | Explained @pytest.mark.parametrize, how each input counts as a separate test run, and reviewed my parameter lists for missing boundary values. |
| Gave Claude my test cases and asked for edge cases and any missing test cases. | Compared the cases with the requirements and validation rules and listed possible edge cases and gaps. I checked each suggestion against the requirement and decided to keep, change or reject it (recorded in the AI review log). |
| Asked how to plan the unit and integration tests in Java and Python. | Explained the difference between unit tests (dependencies mocked) and integration tests (real temporary SQLite database), and suggested which cases belong at which level in each language. |
| Said the team was confused about which functions and tests each of us had implemented, and asked for ideas to organise them. | Suggested grouping tests by feature and test case ID, putting the TC ID in test names and comments, naming test files after their classes, and keeping a mapping from requirements to tests (the RTM). |
| Hit the Ctrl+C error in the Java app and asked for syntax help to handle it. | Showed how to detect a console interrupt and exit cleanly with "Application closed." instead of "Unexpected error. See log.", and how to write tests for each prompt. |
| Drafted Ctrl+C defect notes (DFT-04) and asked Claude to organise them. | Reorganised the notes into the defect log format: where it was found, steps to reproduce, symptom, root cause, fix, verification and lesson learned. |
| Shared the repository and the HTML test reports and asked for the overlapping and duplicate tests as 2 team members rewrote few duplicate tests | Reviewed the test files and reports, and listed overlapping tests by file and function. |
| Shared the Maven error after trimming the shared validation CSV. | Found that sharedFileIsNotEmpty has a threshold suggested lowering the threshold in both the Java and Python tests to match the new file. |
| Asked if the HTML evidence reports update automatically. | explained that the reports are only refreshed when the commands are run again. |
| Shared a PowerShell error when running the SQL check for the report totals test, and the error when sqlite3 was not found. | Explained that PowerShell was breaking the quoted command and that sqlite3 was not installed, and gave a small Python script that runs the same query. |
| Input the drafted test summary report with all the tests numbers, asked to format in md and to calculate the percentages and create tables | Recalculated the numbers from the sheet, corrected the outdated counts, added percentages and counts by area |
| Asked Claude to review my exploratory testing notes and format it in md file | Suggested changes to the notes and the missing Employee App session and formatted in md file |