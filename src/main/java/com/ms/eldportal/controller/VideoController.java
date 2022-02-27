package com.ms.eldportal.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ms.eldportal.model.jpa.VideoDetail;
import com.ms.eldportal.model.request.*;
import com.ms.eldportal.model.response.LoginResponse;
import com.ms.eldportal.model.response.VideoDetailVo;
import com.ms.eldportal.model.search.Content;
import com.ms.eldportal.model.search.Root;
import com.ms.eldportal.model.search.SKey;
import com.ms.eldportal.service.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;


@RestController
public class VideoController {
    @Autowired
    private VideoService service;

    @GetMapping(value = "video/{title}", produces = "video/mp4")
    public Mono<Resource> getVideo(@PathVariable String title) {
        return service.getVideo(title);
    }

    @GetMapping(value = "video/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<VideoDetail> getList() {
        return service.getList();
    }

    @PostMapping(value = "video/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void upload(@RequestParam("file") MultipartFile file,@RequestParam("name") String name,@RequestParam("description") String description,@RequestParam("type") String type,@RequestParam("category") String category) {
        service.upload(file,name,description,type,category);
    }

    @PostMapping(value = "video/like", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void like(@RequestBody UserLikeRequest userLikeRequest) {
        service.like(userLikeRequest);
    }

    @PostMapping(value = "video/rate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public void rate(@RequestBody RatingRequest ratingRequest) {
        service.rate(ratingRequest);
    }

    @PostMapping(value = "video/login", consumes = MediaType.APPLICATION_JSON_VALUE , produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse login(@RequestBody LoginRequest loginRequest,HttpServletRequest httpServletRequest) {
        String baseUrl= ServletUriComponentsBuilder.fromRequestUri(httpServletRequest)
                .replacePath(null)
                .build()
                .toUriString();
        return service.login(loginRequest,baseUrl);
    }

    @PostMapping(value = "video/category", consumes = MediaType.APPLICATION_JSON_VALUE , produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse category(@RequestBody CategoryRequest categoryRequest, HttpServletRequest httpServletRequest) {
        String baseUrl= ServletUriComponentsBuilder.fromRequestUri(httpServletRequest)
                .replacePath(null)
                .build()
                .toUriString();
        return service.getCategoryList(categoryRequest,baseUrl);
    }

    @PostMapping(value="search/data", consumes = MediaType.APPLICATION_JSON_VALUE ,produces = MediaType.APPLICATION_JSON_VALUE)
    public LoginResponse category(@RequestBody SearchKey searchKey, HttpServletRequest httpServletRequest) throws URISyntaxException, JsonProcessingException {
        System.out.println("key:" + searchKey);

        String baseUrl = ServletUriComponentsBuilder.fromRequestUri(httpServletRequest)
                .replacePath(null)
                .build()
                .toUriString();
        LoginResponse loginResponse = new LoginResponse();
        if (searchKey.getSearchValue() != null) {
            RestTemplate restTemplate = new RestTemplate();
            URI uri = new URI("https://eldsearchservicesnew.search.windows.net/indexes/azureblob-index/docs/search?api-version=2021-04-30-Preview");
            SKey body = new SKey();
            body.setSearch(searchKey.getSearchValue());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
            headers.set("api-key", "D729AD21457715934C7F50A09C98B260");
            HttpEntity<SKey> request = new HttpEntity<SKey>(body, headers);
            ObjectMapper mapper = new ObjectMapper();
            ResponseEntity<String> jsonObject = restTemplate.postForEntity(uri, request, String.class);
            Root root = mapper.readValue(jsonObject.getBody(), Root.class);
            List<String> fname = new ArrayList<>();
            if (root.getValue() != null) {
                for (int i = 0; i < root.getValue().size(); i++) {
                    fname.add(root.getValue().get(i).content.getFileName());
                }
            } else {
                return new LoginResponse();
            }
            loginResponse = service.getSearchResults(searchKey, fname, baseUrl);
        } else {
            CategoryRequest categoryRequest = new CategoryRequest();
            categoryRequest.setCategory(searchKey.getCategory().toLowerCase());
            categoryRequest.setUserId(searchKey.getUserId());
            loginResponse = service.getCategoryList(categoryRequest, baseUrl);
        }
        return loginResponse;
    }

    @PostMapping(value = "video", consumes = MediaType.APPLICATION_JSON_VALUE , produces = MediaType.APPLICATION_JSON_VALUE)
    public VideoDetailVo video(@RequestBody VideoRequest videoRequest, HttpServletRequest httpServletRequest) {
        String baseUrl= ServletUriComponentsBuilder.fromRequestUri(httpServletRequest)
                .replacePath(null)
                .build()
                .toUriString();
        return service.video(videoRequest,baseUrl);
    }
}
