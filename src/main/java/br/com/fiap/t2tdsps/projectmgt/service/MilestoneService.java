package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import org.springframework.data.domain.Page;

public interface MilestoneService {

    Page<Milestone> findAll(Integer page, Integer size);

    Page<Milestone> findByProjectId(Long projectId, Integer page, Integer size);

    Milestone findById(Long id);

    Milestone create(Milestone milestone);

    Milestone update(Long id, Milestone updated);

    void delete(Long id);
}
