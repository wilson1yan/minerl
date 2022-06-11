# Copyright (c) 2020 All Rights Reserved
# Author: William H. Guss, Brandon Houghton


from minerl.herobraine.hero.handlers.translation import KeymapTranslationHandler
from minerl.herobraine.hero import spaces
from typing import Tuple
import numpy as np


class DepthObservation(KeymapTranslationHandler):
    """
    Handles Depth observations.
    """

    def to_string(self):
        return 'depth'

    def __repr__(self):
        result = f'DepthObservation(video_resolution={self.video_resolution})'
        result = f'{result}:{self.to_string()}'
        return result

    def xml_template(self) -> str:
        return str("""
            <DepthProducer>
                <Width>{{ video_width }} </Width>
                <Height>{{ video_height }}</Height>
            </DepthProducer>""")

    def __init__(self, video_resolution: Tuple[int, int]):
        self.video_resolution = video_resolution
        space = None

        space = spaces.Box(0, 255, list(video_resolution)[::-1] + [1], dtype=np.float32)
        self.video_depth = 1

        # TODO (R): FIGURE THIS THE FUCK OUT & Document it.
        self.video_height = video_resolution[1]
        self.video_width = video_resolution[0]

        super().__init__(
            hero_keys=["depth"],
            univ_keys=["depth"], space=space)

    def from_hero(self, obs):
        byte_array = super().from_hero(obs)
        depth = np.frombuffer(byte_array, dtype=np.float32)

        if depth is None or len(depth) == 0:
            depth = np.zeros((self.video_height, self.video_width, self.video_depth), dtype=np.float32)
        else:
            depth = depth.reshape((self.video_height, self.video_width, self.video_depth))[::-1, :, :]

        return depth

    def __or__(self, other):
        """
        Combines two Depth observations into one. If all of the properties match return self
        otherwise raise an exception.
        """
        if isinstance(other, DepthObservation) and self.video_resolution == other.video_resolution:
            return DepthObservation(self.video_resolution)
        else:
            raise ValueError("Incompatible observables!")
