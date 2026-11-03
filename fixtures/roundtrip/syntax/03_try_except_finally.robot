*** Settings ***
Documentation     语法边界 03：TRY / EXCEPT / FINALLY
*** Test Cases ***
Try Except Finally
    TRY
        Fail    故意失败
    EXCEPT    *故意失败*    AS    ${err}
        Log    捕获：${err}
    FINALLY
        Log    清理完成
    END
