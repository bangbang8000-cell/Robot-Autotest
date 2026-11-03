package com.qa.autotest.application.repository;

import com.qa.autotest.domain.editor.EditorKeyword;
import java.util.List;
import java.util.Optional;

/** 关键词元数据仓储（EDT-04）：平台级（projectId=0）+ 项目私有。 */
public interface EditorKeywordRepository {

    /** 按项目作用域搜索：返回该项目的私有词条 + 平台级词条。 */
    List<EditorKeyword> search(long projectId, String query, String library, String source, int limit);

    Optional<EditorKeyword> find(long id);

    long create(EditorKeyword keyword, Long operatorId);

    void update(EditorKeyword keyword, Long operatorId);

    void delete(long id, long projectId);
}
