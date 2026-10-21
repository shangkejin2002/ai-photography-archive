declare namespace API {
    type BaseResponse<T> = {
        code: number;
        data: T;
        message: string;
        description?: string | null;
        errorKey?: string | null;
        errorArgs?: Record<string, string | number> | null;
    };

    type LoginParams = {
        userAccount: string;
        userPassword: string;
    };

    type RegisterParams = LoginParams & {
        checkPassword: string;
    };

    type User = {
        id: number;
        username?: string | null;
        userAccount: string;
        avatarUrl?: string | null;
        gender?: number | null;
        userStatus: number;
        userRole: number;
        createTime?: string | null;
        updateTime?: string | null;
    };

    type Photo = {
        id: number;
        originalFilename: string;
        originalUrl: string;
        thumbnailUrl: string;
        contentType: string;
        fileSize: number;
        width: number;
        height: number;
        favorite: boolean;
        cameraMake?: string | null;
        cameraModel?: string | null;
        lensModel?: string | null;
        focalLength?: number | null;
        focalLength35mm?: number | null;
        aperture?: number | null;
        shutterSpeed?: string | null;
        isoValue?: number | null;
        takenAt?: string | null;
        orientation?: number | null;
        exposureBias?: number | null;
        meteringMode?: string | null;
        whiteBalance?: string | null;
        flashStatus?: string | null;
        gpsLatitude?: number | null;
        gpsLongitude?: number | null;
        locationName?: string | null;
        metadataStatus: 'PENDING' | 'EXTRACTED' | 'MISSING' | 'FAILED';
        metadataError?: string | null;
        aiDescription?: string | null;
        localizedAiDescription?: string | null;
        aiTags: PhotoTag[];
        analysisStatus: 'PENDING' | 'ANALYZING' | 'COMPLETED' | 'FAILED';
        analysisError?: string | null;
        analysisModel?: string | null;
        analysisVersion?: string | null;
        analysisAttempts: number;
        analyzedAt?: string | null;
        createTime: string;
        updateTime: string;
    };

    type PhotoTag = {
        name: string;
        type: 'scene' | 'subject' | 'visual_element' | 'color' | 'lighting' | 'weather' | 'composition' |
          'season_visual' | 'general' | 'dominant_color' | 'background_color' | 'foreground_color' |
          'mood' | 'activity' | 'depth' | 'people_presence' | 'people_count' | 'people_prominence';
        confidence?: number | null;
    };

    type PortfolioIntent = {
        year?: number | null;
        season?: 'spring' | 'summer' | 'autumn' | 'winter' | null;
        location?: string | null;
        iso?: number | null;
        favoriteOnly: boolean;
        cameraModel?: string | null;
        lensModel?: string | null;
        subjects: string[];
        colors: string[];
        visualTags: string[];
        excludeTags: string[];
        diversityRequested: boolean;
        orientation?: 'landscape' | 'portrait' | 'square' | null;
        shutterSpeed?: string | null;
        aperture?: number | null;
        focalLength?: number | null;
        hasPeople?: boolean | null;
        peopleCount?: number | null;
        backgroundColors: string[];
        moods: string[];
        requiredTags: string[];
        preferredTags: string[];
        limit: number;
        sort: 'relevance' | 'newest' | 'oldest';
    };

    type PhotoCandidate = {
        photo: Photo;
        score: number;
        matchReasons: string[];
    };

    type AICreatePreview = {
        intent: PortfolioIntent;
        suggestedTitle: string;
        suggestedSubtitle: string;
        candidates: PhotoCandidate[];
        diagnostics: string[];
        suggestions: string[];
    };

    type Portfolio = {
        id: number;
        title: string;
        subtitle?: string | null;
        description?: string | null;
        sourcePrompt?: string | null;
        status: string;
        coverPhotoId?: number | null;
        coverThumbnailUrl?: string | null;
        shareEnabled: boolean;
        shareToken?: string | null;
        shareUrl?: string | null;
        sharedAt?: string | null;
        photos: Photo[];
        createTime: string;
        updateTime: string;
    };

    type InsightItem = {
        label: string;
        count: number;
        percentage: number;
    };

    type MonthlyCount = {
        month: string;
        count: number;
    };

    type ShootingInsights = {
        totalPhotos: number;
        favoritePhotos: number;
        exifPhotos: number;
        aiIndexedPhotos: number;
        gpsPhotos: number;
        namedLocationPhotos: number;
        exifCoverage: number;
        aiCoverage: number;
        gpsCoverage: number;
        monthlyCounts: MonthlyCount[];
        cameras: InsightItem[];
        lenses: InsightItem[];
        apertures: InsightItem[];
        isoValues: InsightItem[];
        focalRanges: InsightItem[];
        locations: InsightItem[];
        subjects: InsightItem[];
        dominantColors: InsightItem[];
        moods: InsightItem[];
        lighting: InsightItem[];
        observation: string;
        observationDetails: string[];
    };
}
