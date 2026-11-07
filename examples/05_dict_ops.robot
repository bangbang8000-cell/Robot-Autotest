*** Settings ***
Documentation     示例套件 5/6：字典读写。
Metadata           CASE_ID    SAMPLE-05-DICT
Metadata           CASE_KIND  sample-basic
Force Tags         sample
Library            Collections

*** Test Cases ***
字典读写
    ${profile}=    Create Dictionary    name=sample    level=1
    Set To Dictionary    ${profile}    level=2
    ${level}=    Get From Dictionary    ${profile}    level
    Should Be Equal As Integers    ${level}    2

字典键集合
    ${profile}=    Create Dictionary    a=1    b=2
    ${keys}=    Get Dictionary Keys    ${profile}    sort_keys=True
    Should Be Equal    ${keys}    ${{ ['a', 'b'] }}
