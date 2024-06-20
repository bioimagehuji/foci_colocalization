// Colocalization of foci (spots) with QuPath
// Usage: Create an Annotation ROI and run the script.

// Get the Annotation ROI drawn by the user
    debug = false
    def roi 
    if (debug) {
        clearAllObjects()
        pixel_size_xy = 0.3
        def plane = ImagePlane.getPlane(0, 0)
        roi = ROIs.createRectangleROI((int)(900/pixel_size_xy), (int)(350/pixel_size_xy), 500/pixel_size_xy, 500/pixel_size_xy, plane)
        user_annotation = PathObjects.createAnnotationObject(roi)
        addObject(user_annotation)
        selectObjects(user_annotation)
    }
    else {
        roi = getSelectedROI()
        user_annotation = getSelectedObject()
    }
    user_annotation.setLocked(true)


// Create sub-annotations that contain objects
    def nuclei_detections = PathObjects.createAnnotationObject(roi)
    nuclei_detections.setName("nuclei_detections")
    nuclei_detections.setLocked(true)
    
    def nuclei_annotations = user_annotation

// Detect nuclei
    selectObjects(nuclei_detections)
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage":"LED-DAPI_Q","requestedPixelSizeMicrons":0.5,"backgroundRadiusMicrons":32.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":1.5,"minAreaMicrons":10.0,"maxAreaMicrons":400.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.1,"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}')
    resetSelection()
    nuclei_detections.getChildObjects().forEach { it.setPathClass(getPathClass("Nucleus"))}
    def nuclei_ann_collection = nuclei_detections.getChildObjects().collect {
        return PathObjects.createAnnotationObject(it.getROI(), it.getPathClass())
    }
    nuclei_annotations.addChildObjects(nuclei_ann_collection)
    removeObject(nuclei_detections, false)
    nuclei_ann_collection = nuclei_annotations.getChildObjects()


// Detect foci
    selectObjects(nuclei_ann_collection)
    print("Finding FITC foci")
    logger.debug("Computing distances for FITC foci");
    createDetectionsFromPixelClassifier("fitc_thresh_laplace5", 0.1, 0.0, "SPLIT")
    selectObjects(nuclei_ann_collection)
    print("Finding Cy5 foci")
    logger.debug("Computing distances for Cy5 foci");
    createDetectionsFromPixelClassifier("cy5_thresh_laplace", 0.1, 0.0, "SPLIT")
    resetSelection()


// Filter out large foci
    def detections_in_roi = getCurrentHierarchy().getObjectsForROI(qupath.lib.objects.PathDetectionObject, roi)
    def fitc_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass("fitc_foci")}
    def cy5_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass("cy5_foci")}
    selectObjects(fitc_foci_in_roi)
    addShapeMeasurements("AREA")
    selectObjects(cy5_foci_in_roi)
    addShapeMeasurements("AREA")
    resetSelection()

    print("fitc: " + fitc_foci_in_roi.size() )
    def delete_fitc = fitc_foci_in_roi.findAll{ hasMeasurement(it, "Area µm^2") && measurement(it, "Area µm^2") > 0.7 }
    print("delete fitc: " + delete_fitc.size() )
    removeObjects(delete_fitc, true)
    
    print("cy5: " + cy5_foci_in_roi.size() )
    def delete_cy5 = cy5_foci_in_roi.findAll{  hasMeasurement(it, "Area µm^2") && measurement(it, "Area µm^2") > 0.7 }
    print("delete cy5: " + delete_cy5.size() )
    removeObjects(delete_cy5, true)

    // refresh detections
    detections_in_roi = getCurrentHierarchy().getObjectsForROI(qupath.lib.objects.PathDetectionObject, roi)
    fitc_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass("fitc_foci")}
    cy5_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass("cy5_foci")}
    print("fitc after delete: " + fitc_foci_in_roi.size() )
    print("cy5 after delete: " + cy5_foci_in_roi.size() )

    
// Calculate distances
    print("Calculating distances between detections")
    var imageData = getCurrentImageData();
    var server = imageData.getServer();
    var cal = server.getPixelCalibration();
    double pixelWidth = cal.getPixelWidth().doubleValue();
    double pixelHeight = cal.getPixelHeight().doubleValue();
    DistanceTools.centroidToCentroidDistance2D(cy5_foci_in_roi, fitc_foci_in_roi, pixelWidth, pixelHeight, "Distance to detection fitc_foci µm");
    DistanceTools.centroidToCentroidDistance2D(fitc_foci_in_roi, cy5_foci_in_roi, pixelWidth, pixelHeight, "Distance to detection cy5_foci µm");


// Find nulcei with foci
    def nuclei_without_foci = nuclei_annotations.getChildObjects().findAll{ it.nChildObjects() == 0 }
    print("nuclei: " + nuclei_annotations.nChildObjects() )
    print("nuclei_without_foci: " + nuclei_without_foci.size() )
    // for debug: selectObjects(nuclei_without_foci)
    removeObjects(nuclei_without_foci, false)


// Colocalization: Find Cy5 and FITC foci which are close enough to each other. 
    colocalization_distance = 0.5
    
    def cy5_coloc = cy5_foci_in_roi.findAll { measurement(it, "Distance to detection fitc_foci µm") <= colocalization_distance  }
    print("cy5 coloc: " + cy5_coloc.size() )
    cy5_coloc_class = getPathClass("cy5_coloc")
    cy5_coloc.forEach { it.setPathClass(cy5_coloc_class)}
    
    def fitc_coloc = fitc_foci_in_roi.findAll { measurement(it, "Distance to detection cy5_foci µm") <= colocalization_distance  }
    print("fitc coloc: " + fitc_coloc.size() )
    fitc_coloc_class = getPathClass("fitc_coloc")
    fitc_coloc.forEach { it.setPathClass(fitc_coloc_class)}
    
// Statistics
    print("CY5 colocalized / all:   " + cy5_coloc.size()  + "/" + cy5_foci_in_roi.size()  + " = " + (cy5_coloc.size() / cy5_foci_in_roi.size()* 100)  + "%")
    print("FITC colocalized / all:  " + fitc_coloc.size() + "/" + fitc_foci_in_roi.size() + " = " + (fitc_coloc.size() / fitc_foci_in_roi.size()* 100)  + "%")
    print("Nuclei with foci: " +  nuclei_annotations.nChildObjects() )
    print("Colocalized Cy5  foci per nucleus:  " + cy5_coloc.size() + "/" + nuclei_annotations.nChildObjects() + " = " + (cy5_coloc.size() / nuclei_annotations.nChildObjects()))
    print("Colocalized FITC foci per nucleus:  " + fitc_coloc.size() + "/" + nuclei_annotations.nChildObjects() + " = " + (fitc_coloc.size() / nuclei_annotations.nChildObjects()))

selectObjects(user_annotation)
