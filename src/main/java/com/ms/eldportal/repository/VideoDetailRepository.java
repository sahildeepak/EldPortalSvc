package com.ms.eldportal.repository;

import com.ms.eldportal.model.jpa.VideoDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import javax.validation.constraints.Max;
import java.util.Collection;
import java.util.List;

public interface VideoDetailRepository extends JpaRepository<VideoDetail, Long> {

    List<VideoDetail> findTop3ByCategoryOrderByRatingDesc(String category);

    List<VideoDetail> findByCategoryOrderByRatingDesc(String category);

    @Query("select v from VideoDetail v where v.fileName in ?1")
    List<VideoDetail> findByFileNameIsIn(List<String> fileNames);
}
