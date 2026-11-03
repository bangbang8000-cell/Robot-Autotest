package com.qa.autotest.application.repository;

import java.util.List;

/** 技术域标签仓储（project_label，AST-04：设备与用例共用同一套标签）。 */
public interface LabelRepository {

    List<String> values(long projectId, String labelKey);

    void add(long projectId, String labelKey, String labelValue, String description, Long operatorId);

    void remove(long projectId, String labelKey, String labelValue);

    boolean exists(long projectId, String labelKey, String labelValue);
}
