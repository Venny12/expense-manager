# Revature Expense Manager

A console-based expense manager with:
- A Python app for employees to submit and manage expenses.
- A Java app for managers to review expenses and view reports.
- One shared SQLite database.

## Required tools

- Python 3.x
- Java JDK 17 or newer
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

## Clone the repository

```powershell
git clone https://github.com/Venny12/expense-manager.git
cd expense-manager
```

## Development status

Initial setup is in progress. Database creation, application run commands,
and test commands will be added as those parts are implemented.

## Team workflow

Both team members write code and tests in Python and Java.
Use a branch for each Jira work item.
Start commit messages with the Jira key.
Have your partner review a pull request before merging into main.