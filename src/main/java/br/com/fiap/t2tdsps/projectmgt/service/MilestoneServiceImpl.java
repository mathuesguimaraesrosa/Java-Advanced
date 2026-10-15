package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.MilestoneAlreadyCompletedException;
import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import br.com.fiap.t2tdsps.projectmgt.model.MilestoneStatus;
import br.com.fiap.t2tdsps.projectmgt.repository.MilestoneRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository milestoneRepository;

    public MilestoneServiceImpl(MilestoneRepository milestoneRepository) {
        this.milestoneRepository = milestoneRepository;
    }

    @Override
    public Page<Milestone> findAll(Integer page, Integer size) {
        return milestoneRepository.findAll(
                Pageable.ofSize(size).withPage(page)
        );
    }

    @Override
    public Page<Milestone> findByProjectId(Long projectId, Integer page, Integer size) {
        return milestoneRepository.findByProjectId(
                projectId,
                Pageable.ofSize(size).withPage(page)
        );
    }

    @Override
    public Milestone findById(Long id) {
        return milestoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", id));
    }

    @Override
    public Milestone create(Milestone milestone) {
        milestone.setId(null);
        return milestoneRepository.save(milestone);
    }

    @Override
    public Milestone update(Long id, Milestone updated) {
        Milestone existing = findById(id); // garante que existe - lança ResourceNotFoundException se não
        if (existing.getStatus() == MilestoneStatus.CONCLUIDA) {
            throw new MilestoneAlreadyCompletedException(id);
        }
        updated.setId(id);
        return milestoneRepository.save(updated);
    }

    @Override
    public void delete(Long id) {
        Milestone milestone = findById(id); // garante que existe - lança ResourceNotFoundException se não
        if (milestone.getStatus() == MilestoneStatus.CONCLUIDA) {
            throw new MilestoneAlreadyCompletedException(id);
        }
        milestoneRepository.delete(milestone);
    }
}
