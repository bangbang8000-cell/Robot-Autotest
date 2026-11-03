*** Settings ***
Documentation    Robot Framework control-flow constructs:
...              IF / ELSE IF / ELSE, FOR, WHILE, TRY / EXCEPT,
...              VAR (RF 7+). All native syntax — Collections is
...              imported only for the small list assertions.
Library    Collections


*** Test Cases ***
IF ELSE Branches
    [Documentation]    Native `IF … ELSE …` block, the modern
    ...                replacement for `Run Keyword If`.
    ${score}=    Set Variable    85
    IF    ${score} >= 90
        ${grade}=    Set Variable    A
    ELSE IF    ${score} >= 80
        ${grade}=    Set Variable    B
    ELSE IF    ${score} >= 70
        ${grade}=    Set Variable    C
    ELSE
        ${grade}=    Set Variable    F
    END
    Should Be Equal    ${grade}    B

FOR Over A List
    ${total}=    Set Variable    ${0}
    FOR    ${n}    IN    1    2    3    4    5
        ${total}=    Evaluate    ${total} + ${n}
    END
    Should Be Equal As Integers    ${total}    15

FOR With Range And Index
    ${seen}=    Create List
    FOR    ${i}    IN RANGE    3
        Append To List    ${seen}    item-${i}
    END
    Lists Should Be Equal    ${seen}    ['item-0', 'item-1', 'item-2']    formatter=repr

WHILE With A Counter
    ${i}=    Set Variable    ${0}
    WHILE    ${i} < 5    limit=10
        ${i}=    Evaluate    ${i} + 1
    END
    Should Be Equal As Integers    ${i}    5

TRY EXCEPT Recovers From Failure
    [Documentation]    `TRY` lets a test continue past an expected
    ...                failure. The matched `EXCEPT` patterns can
    ...                use plain text or glob; here we match any
    ...                exception so the example is robust across
    ...                RF versions whose error wording differs.
    TRY
        Should Be Equal    one    two
    EXCEPT    *    type=GLOB
        Log    Caught the expected mismatch    INFO
    END

TRY EXCEPT FINALLY
    ${cleanup_ran}=    Set Variable    ${False}
    TRY
        Run Keyword And Ignore Error    Should Be True    ${False}
    FINALLY
        ${cleanup_ran}=    Set Variable    ${True}
    END
    Should Be True    ${cleanup_ran}

Native VAR Statement
    [Documentation]    `VAR` (RF 7+) is the modern replacement for
    ...                `Set Variable` / `Set Test Variable` etc.,
    ...                inferring the scope from the prefix.
    VAR    ${greeting}    Hello, World!
    VAR    @{numbers}    1    2    3
    VAR    &{user}    name=Alice    role=admin
    Should Be Equal    ${greeting}    Hello, World!
    Length Should Be    ${numbers}    3
    Should Be Equal    ${user}[name]    Alice
