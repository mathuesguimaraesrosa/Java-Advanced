package br.com.fiap.t2tdsps.projectmgt.service;

import br.com.fiap.t2tdsps.projectmgt.model.Tag;

import java.util.List;

public interface TagService {

    List<Tag> findAll();

    Tag findById(Long id);

    Tag create(Tag tag);

    void delete(Long id);
}
