# Revature Expense Manager

A console-based expense manager with a Python Employee Application, a Java Manager Application and one shared SQLite database.

Employees can submit, view, edit and delete pending expenses and view review history. Managers can review expenses, generate reports, filter by date and export reports to CSV.

## Required tools

- Python 3.11 (the version used for testing)
- Java JDK 17
- Apache Maven
- Git

Check your installations:

```powershell
python --version
java -version
javac -version
mvn -version
git --version
```

The commands below use **Windows PowerShell**. Unless a step says otherwise, run commands from the repository root: the folder containing `sql`, `docs`, `employee-app-python` and `manager-app-java`.

## Clone the repository

```powershell
git clone https://github.com/Venny12/expense-manager.git
cd expense-manager
```

## Set up Python

Create and activate a virtual environment, then install the dependencies:

```powershell
python -m venv employee-app-python/.venv
.\employee-app-python\.venv\Scripts\Activate.ps1
python -m pip install -r employee-app-python/requirements.txt
```

For later sessions, activate the existing environment without creating it again.

If PowerShell blocks activation, you can still use its Python directly:

```powershell
.\employee-app-python\.venv\Scripts\python.exe -m pip install -r employee-app-python/requirements.txt
```

Use that executable in place of `python` in later commands if the environment is not activated.

## Create the shared database

From the repository root:

```powershell
$env:EM_DB_PATH = Join-Path (Get-Location).Path "expense_manager.db"
python sql/init_db.py
```

The setup script creates the schema for an empty database and adds sample data only when all four application tables are empty. It does not reset an existing populated database.

`EM_DB_PATH` is set for the current terminal session. Set it again in each new terminal used to run an application. Both applications must use the same absolute path during manual cross-app testing.

### Sample accounts

These are fictional accounts from `sql/seed.sql` for this exercise:

| Application | Username | Password |
| --- | --- | --- |
| Employee | `employee_maya` | `demo123` |
| Manager | `manager_asha` | `demo123` |

Additional sample accounts are listed in `sql/seed.sql`.

## Run the Employee Application

Activate the Python environment and set `EM_DB_PATH` as described above. Then:

```powershell
cd employee-app-python
python -m employee_app.main
```

Choose `0` to exit. Return to the repository root afterward:

```powershell
cd ..
```

## Run the Manager Application

In a terminal at the repository root, set the database path and run Java:

```powershell
$env:EM_DB_PATH = Join-Path (Get-Location).Path "expense_manager.db"
mvn -f manager-app-java/pom.xml compile exec:java "-Dexec.mainClass=com.revature.expensemanager.Main"
```

Maven downloads the required dependencies on the first run. Choose `0` to exit the application.

## Run Python tests

Activate the Python environment first. From the repository root:

**All Python tests:**

```powershell
python -m pytest employee-app-python/tests -v
```

**One test file:**

```powershell
python -m pytest employee-app-python/tests/unit/test_login.py -v
```

**One test function:**

```powershell
python -m pytest employee-app-python/tests/unit/test_submission_validation.py::test_invalid_amount_is_rejected -v
```

A parameterized function runs once for each input. To run only its zero-amount input:

```powershell
python -m pytest "employee-app-python/tests/unit/test_submission_validation.py::test_invalid_amount_is_rejected[0]" -v
```

**SQL validation tests:**

```powershell
python -m pytest employee-app-python/tests/sql/test_validation_queries.py -v
```

**Smoke tests:**

```powershell
python -m pytest employee-app-python/tests -m smoke -v
```

### Create a Python HTML report

This command runs the tests and saves their results:

```powershell
New-Item -ItemType Directory -Force docs/evidence | Out-Null
python -m pytest employee-app-python/tests -v --html=docs/evidence/python-full-cycle2.html --self-contained-html
```

Open the report:

```powershell
Start-Process docs/evidence/python-full-cycle2.html
```

Use a different filename for each recorded cycle so earlier evidence is preserved.

## Run Java tests

From the repository root:

**All Java tests:**

```powershell
mvn -f manager-app-java/pom.xml clean test
```

**One test class:**

```powershell
mvn -f manager-app-java/pom.xml "-Dtest=AssignedDecisionUnitTest" test
```

**The two assigned manager test classes:**

```powershell
mvn -f manager-app-java/pom.xml "-Dtest=AssignedDecisionUnitTest,AssignedManagerIntegrationTest" test
```

**One test method:**

```powershell
mvn -f manager-app-java/pom.xml "-Dtest=AssignedManagerIntegrationTest#denyPendingExpense" test
```

`-Dtest` selects a class or method. `pom.xml` supplies Maven's dependencies and build/test settings. If you are already inside `manager-app-java`, the all-tests command is simply `mvn clean test`.


## How the automated tests work

- Unit tests check individual rules. Some use mocked dependencies.
- Integration tests run real application components against temporary SQLite databases.
- SQL tests check schema constraints and validation queries.
- Test inputs are supplied automatically; you do not type into the menus during automated runs.
- Automated database tests create their own test databases rather than relying on the populated shared project database.

The latest confirmed full runs had **280 Python checks and 158 Java checks passing**. These include parameterized inputs and differ from the number of designed test cases. Future changes may change these counts.

## Manual cross-app testing

Run both applications against the same `EM_DB_PATH`. Follow the current cases in `docs/Test_Cases.md`, record the cycle and results, save screenshots under `docs/evidence/`, and update `docs/rtm.xlsx`.

Use fresh expenses for repeated scenarios so earlier approvals or deletions do not affect the next run.

## Project folders

| Folder | Contents |
| --- | --- |
| `employee-app-python/employee_app/` | Employee application code |
| `employee-app-python/tests/` | Python unit, integration and SQL tests |
| `manager-app-java/src/main/java/` | Manager application code |
| `manager-app-java/src/test/java/` | Java tests |
| `sql/` | Schema, seed data, initialization and validation queries |
| `shared-test-data/` | Shared test data |
| `docs/` | Test documentation and RTM |
| `docs/evidence/` | Saved test reports and manual evidence |
| `logs/` | Application logs |
| `reports/` | Exported report CSV files |

## Team workflow

Both team members write code and tests in Python and Java. Use a branch for each Jira work item, start commit messages with the Jira key, and have your partner review a pull request before merging into `main`.
