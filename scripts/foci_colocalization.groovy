// Colocalization of foci (spots) with QuPath
// Usage: Create an Annotation ROI and run the script.

// User parameters
    channel_to_classifier = [
        "LED-Fitc_Q (C2)": "fitc_thresh_laplace5",
        "LED-Tritc_Q (C3)": "BRDU_thresh_laplace",
        "LED-Cy5_Q (C4)":  "cy5_thresh_laplace",
    ]
    channel_to_foci_class = [
        "LED-Fitc_Q (C2)": "fitc_foci",
        "LED-Tritc_Q (C3)": "brdu_foci",
        "LED-Cy5_Q (C4)":  "cy5_foci",
    ]
    channel_to_coloc_class = [
        "LED-Fitc_Q (C2)": "fitc_coloc",
        "LED-Tritc_Q (C3)": "brdu_coloc",
        "LED-Cy5_Q (C4)":  "cy5_coloc",
    ]
    debug = false
    

// Get the Annotation ROI drawn by the user
    def roi 
    if (!debug) {
        roi = getSelectedROI()
        user_annotation = getSelectedObject()
    }
    else {
        clearAllObjects()
        pixel_size_xy = 0.3
        def plane = ImagePlane.getPlane(0, 0)
        roi = ROIs.createRectangleROI((int)(5500/pixel_size_xy), (int)(2350/pixel_size_xy), 1000/pixel_size_xy, 1000/pixel_size_xy, plane)
        user_annotation = PathObjects.createAnnotationObject(roi)
        addObject(user_annotation)
        selectObjects(user_annotation)
    }
    user_annotation.setLocked(true)
    def nuclei_parent_annotation = user_annotation
    nuclei_parent_annotation.setName("nuclei_parent_annotation")
    

// Channels
    assert getCurrentViewer().getImageDisplay().selectedChannels.size() == 2, "Please select exactly 2 channels in the Brightness & Contrast pane. Selected: " + getCurrentViewer().getImageDisplay().selectedChannels
    channel1 = getCurrentViewer().getImageDisplay().selectedChannels[0].name
    channel2 = getCurrentViewer().getImageDisplay().selectedChannels[1].name
    println("Channel 1: " + channel1)
    println("Channel 2: " + channel2)

// Create sub-annotations that contain objects
    def tmp_nuclei_dets_parent = PathObjects.createAnnotationObject(roi)
    addObject(tmp_nuclei_dets_parent)
    tmp_nuclei_dets_parent.setName("tmp_nuclei_dets_parent")
    tmp_nuclei_dets_parent.setLocked(true)


// Detect nuclei
    selectObjects(tmp_nuclei_dets_parent)
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', 
          '{"detectionImage":"LED-DAPI_Q","requestedPixelSizeMicrons":0.5,"backgroundRadiusMicrons":32.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":3,"minAreaMicrons":10.0,"maxAreaMicrons":700.0,"threshold":10.0,"watershedPostProcess":true,"cellExpansionMicrons":0.1,"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}')
    // Classify nuclei into two classes: Either "pro-phase or meta-phase" or "interphase"
    
    runObjectClassifier("phase_dapi13_cells");
    // Convert detections to annotations (for foci detection)
    resetSelection()
    def nuclei_ann_collection = tmp_nuclei_dets_parent.getChildObjects().collect {
        ann_obj = PathObjects.createAnnotationObject(it.getROI(), it.getPathClass())
        ann_obj.setPathClass(it.getPathClass())
        return ann_obj
    }
    nuclei_parent_annotation.addChildObjects(nuclei_ann_collection)
    removeObject(tmp_nuclei_dets_parent, false)
    nuclei_ann_collection = nuclei_parent_annotation.getChildObjects()


// Classifiers
    classifier1 = channel_to_classifier[channel1]
    println "Classifier 1: " + classifier1 + "  (for channel '" + channel1 + "' )"
    assert classifier1 != null, "could not find a classifier for channel '" + channel1 + "' in keys of channel_to_classifier: " + channel_to_classifier.keySet()
    classifier2 = channel_to_classifier[channel2]
    println "Classifier 2: " + classifier2 + "  (for channel " + channel2 + " )"
    assert classifier2 != null, "could not find a classifier for channel '" + channel2 + "' in keys of channel_to_classifier: " + channel_to_classifier.keySet()


// Detect foci
    selectObjects(nuclei_ann_collection)
    print("Finding channel1 foci")
    logger.debug("Computing distances for channel1 foci");
    createDetectionsFromPixelClassifier(classifier1, 0.1, 0.0, "SPLIT")
    foci_class_name1 = channel_to_foci_class[channel1]
    resetSelection()
    
    selectObjects(nuclei_ann_collection)
    logger.debug("Computing distances for channel2 foci");
    createDetectionsFromPixelClassifier(classifier2, 0.1, 0.0, "SPLIT")
    foci_class_name2 = channel_to_foci_class[channel2]
    resetSelection()


// Filter out large foci
    def detections_in_roi = getCurrentHierarchy().getObjectsForROI(qupath.lib.objects.PathDetectionObject, roi)
    def class1_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass(foci_class_name1)}
    def class2_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass(foci_class_name2)}
    print(foci_class_name1 + ": " + class1_foci_in_roi.size() )
    print(foci_class_name2 + ": " + class2_foci_in_roi.size() )

    addShapeMeasurements("AREA")
    selectObjects(class1_foci_in_roi)
    addShapeMeasurements("AREA")
    selectObjects(class2_foci_in_roi)
    addShapeMeasurements("AREA")
    resetSelection()

    print("Deleting foci larger than 0.7 µm^2");
    def deleted_foci1 = class1_foci_in_roi.findAll{ hasMeasurement(it, "Area µm^2") && measurement(it, "Area µm^2") > 0.7 }
    print("Deleted " + foci_class_name1 + ": "  + deleted_foci1.size() )
    removeObjects(deleted_foci1, true)
    
    def deleted_foci2 = class2_foci_in_roi.findAll{ hasMeasurement(it, "Area µm^2") && measurement(it, "Area µm^2") > 0.7 }
    print("Deleted " + foci_class_name2 + ": "  + deleted_foci2.size() )
    removeObjects(deleted_foci2, true)


// refresh detections
    detections_in_roi = getCurrentHierarchy().getObjectsForROI(qupath.lib.objects.PathDetectionObject, roi)
    class1_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass(foci_class_name1)}
    class2_foci_in_roi = detections_in_roi.findAll {it.getPathClass() == getPathClass(foci_class_name2)}
    print(foci_class_name1 + " after delete: " + class1_foci_in_roi.size() )
    print(foci_class_name2 + " after delete: " + class2_foci_in_roi.size() )

    
// Calculate distances
    print("Calculating distances between detections")
    var cal = getCurrentServer().getPixelCalibration();
    double pixelWidth = cal.getPixelWidth().doubleValue();
    double pixelHeight = cal.getPixelHeight().doubleValue();
    DistanceTools.centroidToCentroidDistance2D(class1_foci_in_roi, class2_foci_in_roi, pixelWidth, pixelHeight, "Distance to detection " + foci_class_name2 + " µm");
    DistanceTools.centroidToCentroidDistance2D(class2_foci_in_roi, class1_foci_in_roi, pixelWidth, pixelHeight, "Distance to detection " + foci_class_name1 + " µm");


// Find nulcei with foci
    def nuclei_without_foci = nuclei_parent_annotation.getChildObjects().findAll{ it.nChildObjects() == 0 }
    print("Nuclei: " + nuclei_parent_annotation.nChildObjects() )
    print("Nuclei_without_foci: " + nuclei_without_foci.size() )
    // for debug: selectObjects(nuclei_without_foci)
    removeObjects(nuclei_without_foci, false)


// Colocalization: Find Cy5 and FITC foci which are close enough to each other. 
    colocalization_distance = 0.7
    
    def coloc_detections1 = class1_foci_in_roi.findAll { measurement(it, "Distance to detection " + foci_class_name2 + " µm") <= colocalization_distance  }
    coloc_class_name1 = channel_to_coloc_class[channel1]
    print(coloc_class_name1 + ": " + coloc_detections1.size() )
    coloc1_pathclass = getPathClass(coloc_class_name1)
    coloc_detections1.forEach { it.setPathClass(coloc1_pathclass)}
    
    def coloc_detections2 = class2_foci_in_roi.findAll { measurement(it, "Distance to detection " + foci_class_name1 + " µm") <= colocalization_distance  }
    coloc_class_name2 = channel_to_coloc_class[channel2]
    print(coloc_class_name2 + ": " + coloc_detections2.size() )
    coloc2_pathclass = getPathClass(coloc_class_name2)
    coloc_detections2.forEach { it.setPathClass(coloc2_pathclass)}

    
// Statistics
    print("All phases:");
    print("===========");
    print(coloc_class_name1 + "/" + foci_class_name1 + ":   " + coloc_detections1.size()  + "/" + class1_foci_in_roi.size()  + " = " + (coloc_detections1.size() / (class1_foci_in_roi.size()+0.001)* 100)  + "%")
    print(coloc_class_name2 + "/" + foci_class_name2 + ":   " + coloc_detections2.size() + "/" + class2_foci_in_roi.size() + " = " + (coloc_detections2.size() / (class2_foci_in_roi.size()+0.001)* 100)  + "%")
    print("Nuclei with foci: " +  nuclei_parent_annotation.nChildObjects() )
    print(coloc_class_name1 + " foci per nucleus with foci:  " + coloc_detections1.size() + "/" + nuclei_parent_annotation.nChildObjects() + " = " + (coloc_detections1.size() / nuclei_parent_annotation.nChildObjects()))
    print(coloc_class_name2 + " foci per nucleus with foci:  " + coloc_detections2.size() + "/" + nuclei_parent_annotation.nChildObjects() + " = " + (coloc_detections2.size() / nuclei_parent_annotation.nChildObjects()))


    def phases = ["pro/meta-phase", "interphase"]
    def phase_nucs 
    for (phase in phases) {
        print(phase + ":");
        print("=========");
        def coloc1_phase = 0
        def coloc2_phase = 0
        def foci1_phase = 0
        def foci2_phase = 0
        phase_nucs = nuclei_parent_annotation.getChildObjects().findAll{
            it.getPathClass() == getPathClass(phase) 
        }
        phase_nucs.each { annotation ->
            def detections = getCurrentHierarchy().getObjectsForROI(qupath.lib.objects.PathDetectionObject, annotation.getROI())
            detections.each { det ->
                if (det.getPathClass() == getPathClass(coloc_class_name1)) {
                    coloc1_phase += 1
                    foci1_phase += 1
                }
                if (det.getPathClass() == getPathClass(foci_class_name1)) {
                    foci1_phase += 1
                }
                if (det.getPathClass() == getPathClass(coloc_class_name2)) {
                    coloc2_phase += 1
                    foci2_phase += 1
                }
                if (det.getPathClass() == getPathClass(foci_class_name2)) {
                    foci2_phase += 1
                }

            }
        }
        print(coloc_class_name1 + "/" + foci_class_name1 + ":   " + coloc1_phase  + "/" + foci1_phase   + " = " + (coloc1_phase / (foci1_phase+0.0001) * 100)  + "%")
        print(coloc_class_name2 + "/" + foci_class_name2 + ":   " + coloc2_phase + "/" + foci2_phase  + " = " + (coloc2_phase / (foci2_phase+0.0001) * 100)  + "%")
        print("Nuclei with foci: " +  phase_nucs.size() )
    }
selectObjects(user_annotation)
