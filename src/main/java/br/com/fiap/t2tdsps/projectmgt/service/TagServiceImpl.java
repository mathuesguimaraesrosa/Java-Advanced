package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.exceptions.ResourceNotFoundException;
import br.com.fiap.t2tdsps.projectmgt.model.Tag;
import br.com.fiap.t2tdsps.projectmgt.repository.TagRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    public TagServiceImpl(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Override
    public List<Tag> findAll() {
        return tagRepository.findAll();
    }

    @Override
    public Tag findById(Long id) {
        return tagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", id));
    }

    @Override
    public Tag create(Tag tag) {
        tag.setId(null);
        return tagRepository.save(tag);
    }

    @Override
    public void delete(Long id) {
        Tag tag = findById(id);
        tagRepository.delete(tag);
    }
}
