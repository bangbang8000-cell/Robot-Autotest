*** Settings ***
Documentation     语法边界 01：FOR + IN ENUMERATE，且块内再嵌一层 FOR
*** Variables ***
@{ITEMS}          alpha    beta    gamma
*** Test Cases ***
For In Enumerate Nested
    FOR    ${i}    ${item}    IN ENUMERATE    @{ITEMS}
        Log    第 ${i} 个：${item}
        FOR    ${j}    IN RANGE    1    3
            Log    内层 ${j}
        END
    END
