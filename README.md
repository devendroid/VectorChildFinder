# VectorChildFinder
[![Android Arsenal]( https://img.shields.io/badge/Android%20Arsenal-VectorChildFinder-green.svg?style=flat )]( https://android-arsenal.com/details/1/6733 )
[![](https://jitpack.io/v/devsideal/VectorChildFinder.svg)](https://jitpack.io/#devsideal/VectorChildFinder/1.0.0)
[![GitHub license](https://img.shields.io/github/license/dcendents/android-maven-gradle-plugin.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

VectorChildFinder helps to find inner elements of vector drawable like path and group.
## Demo
![VectorChildFinder](/assets/vector1.0.0.gif)

## Dependency
- Add the dependencies to your gradle files:

#### Step 1. Add it in your root build.gradle at the end of repositories
```gradle
   allprojects {
       repositories {
    	...
    	maven { url 'https://jitpack.io' }
    	}
    }
```

#### Step 2. Add the dependency
```gradle
    dependencies {
        compile 'com.github.devsideal:VectorChildFinder:1.0.0'
     }

```

## Usage
```java
VectorChildFinder vector = new VectorChildFinder(this, R.drawable.my_vector, imageView);

VectorDrawableCompat.VFullPath path1 = vector.findPathByName("path1");
path1.setFillColor(Color.RED);

VectorDrawableCompat.VGroup group1 = vector.findGroupByName("group1");
group1.setTranslateX(10);

imageView.invalidate();

```

## Used by

<img src="/assets/gym.png" />  

[Gym Scheduler][Gym Scheduler]

## License
```
Copyright 2018 Deven Singh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

[Gym Scheduler]:  https://play.google.com/store/apps/details?id=com.devs.gym