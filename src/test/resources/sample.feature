@smoke
Feature: Login functionality

  Background:
    Given the user is on the login page

  @positive
  Scenario: Successful login
    When the user enters valid credentials
    Then the user should be redirected to the dashboard

  @negative
  Scenario: Failed login
    When the user enters incorrect credentials
    Then an error message should be displayed
    And the user remains on the login page

  Scenario Outline: Login attempts with multiple inputs
    When the user enters "<username>" and "<password>"
    Then the system should respond with "<message>"

    Examples:
      | username | password | message                |
      | user1    | pass123  | Login successful       |
      | user2    | wrong    | Invalid credentials    |
