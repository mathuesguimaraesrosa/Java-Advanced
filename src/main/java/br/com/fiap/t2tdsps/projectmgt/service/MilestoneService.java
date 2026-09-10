package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Milestone;
import br.com.fiap.t2tdsps.projectmgt.repository.MilestoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MilestoneService {

    private final MilestoneRepository milestoneRepository;

    public MilestoneService(MilestoneRepository milestoneRepository) {
        this.milestoneRepository = milestoneRepository;
    }

    public List<Milestone> findAll() {
        return milestoneRepository.findAll();
    }

    public Optional<Milestone> findById(Long id) {
        return milestoneRepository.findById(id);
    }

    public Milestone create(Milestone milestone) {
        milestone.setId(null);
        return milestoneRepository.save(milestone);
    }

    public Optional<Milestone> update(Long id, Milestone updated) {
        return milestoneRepository.findById(id).map(existing -> {
            updated.setId(id);
            return milestoneRepository.save(updated);
        });
    }

    public boolean delete(Long id) {
        if (!milestoneRepository.existsById(id)) {
            return false;
        }
        milestoneRepository.deleteById(id);
        return true;
    }
}