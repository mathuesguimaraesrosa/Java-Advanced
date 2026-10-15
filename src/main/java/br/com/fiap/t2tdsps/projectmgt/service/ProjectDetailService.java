package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.ProjectDetail;

public interface ProjectDetailService {

    ProjectDetail findByProjectId(Long projectId);

    ProjectDetail createOrUpdate(Long projectId, ProjectDetail detail);
}
