package com.ms.eldportal.service;

import com.azure.spring.autoconfigure.storage.resource.AzureStorageResourcePatternResolver;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.ms.eldportal.model.jpa.Rating;
import com.ms.eldportal.model.jpa.UserLike;
import com.ms.eldportal.model.jpa.UserProfile;
import com.ms.eldportal.model.jpa.VideoDetail;
import com.ms.eldportal.model.request.*;
import com.ms.eldportal.model.response.CategoryVo;
import com.ms.eldportal.model.response.LoginResponse;
import com.ms.eldportal.model.response.VideoDetailVo;
import com.ms.eldportal.repository.RatingRepository;
import com.ms.eldportal.repository.UserLikeRepository;
import com.ms.eldportal.repository.UserProfileRepository;
import com.ms.eldportal.repository.VideoDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import java.io.IOException;
import java.sql.Date;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoService {
    public static Logger logger = LoggerFactory.getLogger(VideoService.class);
    private final VideoDetailRepository videoDetailRepository;
    private final UserLikeRepository userLikeRepository;
    private final UserProfileRepository userRepository;
    private final RatingRepository ratingRepository;

    @Autowired
    private AzureStorageResourcePatternResolver resourceLoader;

    @Autowired
    private BlobServiceClient blobServiceClient;

    public VideoService(VideoDetailRepository videoDetailRepository, UserLikeRepository userLikeRepository, UserProfileRepository userRepository, RatingRepository ratingRepository) {
        this.videoDetailRepository = videoDetailRepository;
        this.userLikeRepository = userLikeRepository;
        this.userRepository = userRepository;
        this.ratingRepository = ratingRepository;
    }

    public List<VideoDetail> getList() {
        return videoDetailRepository.findAll();
    }

    public Mono<Resource> getVideo(String title) {
        return Mono.fromSupplier(() -> resourceLoader.getResource("azure-blob://eldlmsstoragecontainer/"+title));
    }

    public void upload(MultipartFile file, String name, String description, String type, String category) {
        BlobClient blobClient = blobServiceClient.getBlobContainerClient("eldlmsstoragecontainer").getBlobClient(file.getOriginalFilename());

        try {
            blobClient.upload(file.getInputStream(),file.getSize(),true);
        } catch (IOException e) {
            e.printStackTrace();
        }
        VideoDetail videoDetail=new VideoDetail();
        videoDetail.setRating(Long.valueOf(0));
        videoDetail.setTotalNoOfLikes(Long.valueOf(0));
        videoDetail.setDescription(description);
        videoDetail.setAuthor("Rohit Roy");
        videoDetail.setCategory(category);
        videoDetail.setUploadedBy("admin");
        videoDetail.setFileName(file.getOriginalFilename());
        videoDetail.setCreationTime(new Date(System.currentTimeMillis()));
        videoDetail.setName(name);

        videoDetailRepository.save(videoDetail);
    }

    @Transactional
    public void like(UserLikeRequest userLikeRequest) {
        UserProfile userProfile=userRepository.findByUserName(userLikeRequest.getUserId());
        Optional<VideoDetail> videoDetailOptional=videoDetailRepository.findById(userLikeRequest.getVideoId());

        if(videoDetailOptional.isPresent()
                && userProfile!=null){
            VideoDetail videoDetail=videoDetailOptional.get();
            if(!userLikeRequest.getLike()){
                Long countUserLike=userLikeRepository.countUserLikeByVideoDetailAndUser(videoDetail,userProfile);
                if(countUserLike>0){
                    userLikeRepository.deleteByVideoDetailAndUser(videoDetail,userProfile);
                    videoDetail.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes()-1);
                    videoDetailRepository.save(videoDetail);
                }
            }else {
                UserLike userLike = new UserLike();
                userLike.setUser(userProfile);
                userLike.setVideoDetail(videoDetailOptional.get());
                userLikeRepository.save(userLike);
                videoDetail.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes() + 1);
                videoDetailRepository.save(videoDetail);
            }
        }
    }

    public void rate(RatingRequest ratingRequest) {
        UserProfile userProfile=userRepository.findByUserName(ratingRequest.getUserId());
        Optional<VideoDetail> videoDetailOptional=videoDetailRepository.findById(ratingRequest.getVideoId());
        if(videoDetailOptional.isPresent()
                && userProfile!=null){
            VideoDetail videoDetail=videoDetailOptional.get();

            Rating existingRating=ratingRepository.findByVideoDetailAndUser(videoDetail,userProfile);
            Long countRating=ratingRepository.countRatingByVideoDetail(videoDetail);
            if(existingRating!=null){
                Long newAverageRating=((videoDetail.getRating()*countRating)+ratingRequest.getRating()-existingRating.getRating())/(countRating);
                videoDetail.setRating(newAverageRating);
                existingRating.setRating(ratingRequest.getRating());
                ratingRepository.save(existingRating);
            }else{
                Long newAverageRating=((videoDetail.getRating()*countRating)+ratingRequest.getRating())/(countRating+1);
                videoDetail.setRating(newAverageRating);
                Rating rating=new Rating();
                rating.setUser(userProfile);
                rating.setVideoDetail(videoDetail);
                rating.setRating(ratingRequest.getRating());
                ratingRepository.save(rating);
            }
            videoDetailRepository.save(videoDetail);
        }

    }

    public LoginResponse login(LoginRequest loginRequest, String baseUrl) {
        UserProfile userProfile=userRepository.findByUserNameAndPassword(loginRequest.getUsername(),loginRequest.getPassword());
        if(userProfile!=null){
            List<VideoDetail> videoDetails=videoDetailRepository.findTop3ByCategoryOrderByRatingDesc(userProfile.getCategory());
            LoginResponse loginResponse=new LoginResponse();
            loginResponse.setPassword(userProfile.getPassword());
            loginResponse.setUserId(userProfile.getUserName());
            List<CategoryVo> categoryVoList=new ArrayList<>();
            CategoryVo categoryVo=new CategoryVo();
            categoryVo.setCategory(userProfile.getCategory());
            List<VideoDetailVo> videoDetailVoList= new ArrayList<>();
            categoryVo.setVideoList(videoDetailVoList);
            categoryVoList.add(categoryVo);
            loginResponse.setCategoryWiseVideo(categoryVoList);
            videoDetails.stream().forEach(videoDetail -> {
                Long countRating=userLikeRepository.countUserLikeByVideoDetailAndUser(videoDetail,userProfile);
                VideoDetailVo videoDetailVo=new VideoDetailVo();
                if(countRating>0){
                    videoDetailVo.setLike(true);
                }else{
                    videoDetailVo.setLike(false);
                }
                videoDetailVo.setCategory(videoDetail.getCategory());
                videoDetailVo.setUploadedBy(videoDetail.getUploadedBy());
                videoDetailVo.setName(videoDetail.getName());
                videoDetailVo.setFileName(baseUrl+"/video/"+videoDetail.getFileName());
                videoDetailVo.setAuthor(videoDetail.getAuthor());
                videoDetailVo.setDescription(videoDetail.getDescription());
                videoDetailVo.setCurrentUser(userProfile.getUserName());
                videoDetailVo.setRating(videoDetail.getRating());
                videoDetailVo.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes());
                videoDetailVo.setId(videoDetail.getId());
                videoDetailVo.setCreationTime(videoDetail.getCreationTime());
                videoDetailVo.setVtt(videoDetail.getVttURL());
                videoDetailVo.setThumbneil(videoDetail.getThumbneilURL());
                videoDetailVoList.add(videoDetailVo);
            });
            return loginResponse;
        }
        return null;
    }


    public LoginResponse getCategoryList(CategoryRequest categoryRequest, String baseUrl) {
        List<VideoDetail> videoDetails=null;

        UserProfile userProfile=userRepository.findByUserName(categoryRequest.getUserId());

        if("All".equalsIgnoreCase(categoryRequest.getCategory())){
            videoDetails=videoDetailRepository.findAll();
        }else{
            videoDetails = videoDetailRepository.findByCategoryOrderByRatingDesc(categoryRequest.getCategory());
        }
        LoginResponse loginResponse=new LoginResponse();
        loginResponse.setUserId(categoryRequest.getUserId());
        List<CategoryVo> categoryVoList=new ArrayList<>();
        loginResponse.setCategoryWiseVideo(categoryVoList);
        Map<String,List<VideoDetail>> categoryWiseVideo=videoDetails.stream().collect(Collectors.groupingBy(VideoDetail::getCategory));
        categoryWiseVideo.forEach((category,videoDetailList)->{
            CategoryVo categoryVo=new CategoryVo();
            categoryVo.setCategory(category);
            List<VideoDetailVo> videoDetailVoList= new ArrayList<>();
            categoryVo.setVideoList(videoDetailVoList);
            categoryVoList.add(categoryVo);
            videoDetailList.stream().forEach(videoDetail -> {
                VideoDetailVo videoDetailVo=new VideoDetailVo();
                if(userProfile!=null){
                    Long countLike=userLikeRepository.countUserLikeByVideoDetailAndUser(videoDetail,userProfile);
                    videoDetailVo.setCurrentUser(userProfile.getUserName());
                    if(countLike>0){
                        videoDetailVo.setLike(true);
                    }else{
                        videoDetailVo.setLike(false);
                    }
                }else{
                    videoDetailVo.setLike(false);
                }
                videoDetailVo.setCategory(videoDetail.getCategory());
                videoDetailVo.setUploadedBy(videoDetail.getUploadedBy());
                videoDetailVo.setName(videoDetail.getName());
                videoDetailVo.setFileName(baseUrl+"/video/"+videoDetail.getFileName());
                videoDetailVo.setAuthor(videoDetail.getAuthor());
                videoDetailVo.setDescription(videoDetail.getDescription());
                videoDetailVo.setRating(videoDetail.getRating());
                videoDetailVo.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes());
                videoDetailVo.setId(videoDetail.getId());
                videoDetailVo.setCreationTime(videoDetail.getCreationTime());
                videoDetailVo.setVtt(videoDetail.getVttURL());
                videoDetailVo.setThumbneil(videoDetail.getThumbneilURL());
                videoDetailVoList.add(videoDetailVo);
            });
            Collections.sort(categoryVo.getVideoList());
        });
        return loginResponse;

    }


    public LoginResponse getSearchResults(SearchKey searchKey,List<String> fname,  String baseUrl) {
        List<VideoDetail> videoDetails=null;

        UserProfile userProfile=userRepository.findByUserName(searchKey.getUserId());

        videoDetails=videoDetailRepository.findByFileNameIsIn(fname);

        LoginResponse loginResponse=new LoginResponse();
        loginResponse.setUserId(searchKey.getUserId());
        List<CategoryVo> categoryVoList=new ArrayList<>();
        loginResponse.setCategoryWiseVideo(categoryVoList);
        Map<String,List<VideoDetail>> categoryWiseVideo=videoDetails.stream().collect(Collectors.groupingBy(VideoDetail::getCategory));
        categoryWiseVideo.forEach((category,videoDetailList)->{
            CategoryVo categoryVo=new CategoryVo();
            categoryVo.setCategory(category);
            List<VideoDetailVo> videoDetailVoList= new ArrayList<>();
            categoryVo.setVideoList(videoDetailVoList);
            categoryVoList.add(categoryVo);
            videoDetailList.stream().forEach(videoDetail -> {
                VideoDetailVo videoDetailVo=new VideoDetailVo();
                if(userProfile!=null){
                    Long countLike=userLikeRepository.countUserLikeByVideoDetailAndUser(videoDetail,userProfile);
                    videoDetailVo.setCurrentUser(userProfile.getUserName());
                    if(countLike>0){
                        videoDetailVo.setLike(true);
                    }else{
                        videoDetailVo.setLike(false);
                    }
                }else{
                    videoDetailVo.setLike(false);
                }
                videoDetailVo.setCategory(videoDetail.getCategory());
                videoDetailVo.setUploadedBy(videoDetail.getUploadedBy());
                videoDetailVo.setName(videoDetail.getName());
                videoDetailVo.setFileName(baseUrl+"/video/"+videoDetail.getFileName());
                videoDetailVo.setAuthor(videoDetail.getAuthor());
                videoDetailVo.setDescription(videoDetail.getDescription());
                videoDetailVo.setRating(videoDetail.getRating());
                videoDetailVo.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes());
                videoDetailVo.setId(videoDetail.getId());
                videoDetailVo.setCreationTime(videoDetail.getCreationTime());
                videoDetailVo.setVtt(videoDetail.getVttURL());
                videoDetailVo.setThumbneil(videoDetail.getThumbneilURL());
                videoDetailVoList.add(videoDetailVo);
            });
            Collections.sort(categoryVo.getVideoList());
        });
        return loginResponse;

    }

    public VideoDetailVo video(VideoRequest videoRequest, String baseUrl) {
        VideoDetailVo videoDetailVo=new VideoDetailVo();
        Optional<VideoDetail> optionalVideoDetail=videoDetailRepository.findById(videoRequest.getVideoId());
        UserProfile userProfile=userRepository.findByUserName(videoRequest.getUserId());
        if(optionalVideoDetail.isPresent()
                && userProfile!=null){
            VideoDetail videoDetail=optionalVideoDetail.get();

            Long countLike=userLikeRepository.countUserLikeByVideoDetailAndUser(videoDetail,userProfile);
            videoDetailVo.setCurrentUser(userProfile.getUserName());
            if(countLike>0){
                videoDetailVo.setLike(true);
            }else{
                videoDetailVo.setLike(false);
            }

            videoDetailVo.setCategory(videoDetail.getCategory());
            videoDetailVo.setUploadedBy(videoDetail.getUploadedBy());
            videoDetailVo.setName(videoDetail.getName());
            videoDetailVo.setFileName(baseUrl+"/video/"+videoDetail.getFileName());
            videoDetailVo.setAuthor(videoDetail.getAuthor());
            videoDetailVo.setDescription(videoDetail.getDescription());
            videoDetailVo.setRating(videoDetail.getRating());
            videoDetailVo.setTotalNoOfLikes(videoDetail.getTotalNoOfLikes());
            videoDetailVo.setId(videoDetail.getId());
            videoDetailVo.setCreationTime(videoDetail.getCreationTime());
            videoDetailVo.setVtt(videoDetail.getVttURL());
            videoDetailVo.setThumbneil(videoDetail.getThumbneilURL());
        }
        return  videoDetailVo;
    }
}
